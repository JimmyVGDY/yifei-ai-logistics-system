package jimmy.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import jimmy.mapper.AuthMapper;
import jimmy.model.CaptchaResponse;
import jimmy.model.LoginConflictResponse;
import jimmy.model.LoginRequest;
import jimmy.model.LoginResponse;
import jimmy.model.MenuVO;
import jimmy.model.PasswordChangeRequest;
import jimmy.model.ProfileUpdateRequest;
import jimmy.model.RequireCaptchaResponse;
import jimmy.util.FieldEncryptor;
import jimmy.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 认证授权服务 —— 统一管理登录、登出、会话保持、权限装配和个人资料管理。
 * <p>
 * 核心流程：
 * <ol>
 *   <li><b>密码校验</b>：兼容明文（自动升级 BCrypt）与 BCrypt 编码</li>
 *   <li><b>冲突检测</b>：同一账号已有在线会话时创建 {@link LoginConflictService} 确认流程</li>
 *   <li><b>权限装配</b>：从 sys_role_menu + sys_user_role 查询并扁平化权限码，写入 Sa-Token 会话</li>
 *   <li><b>菜单树构建</b>：将扁平菜单按 parentId 组装为前端侧边栏需要的树形结构</li>
 * </ol>
 *
 * @see LoginConflictService 登录冲突管理
 * @see SystemPermissionService 权限管理
 */
@Slf4j
@Service
public class AuthService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final AuthMapper authMapper;
    private final SystemPermissionService systemPermissionService;
    private final LoginConflictService loginConflictService;
    private final FieldEncryptor fieldEncryptor;
    private final LoginSecurityService loginSecurityService;

    public AuthService(AuthMapper authMapper, SystemPermissionService systemPermissionService,
                       LoginConflictService loginConflictService, FieldEncryptor fieldEncryptor,
                       LoginSecurityService loginSecurityService) {
        this.authMapper = authMapper;
        this.systemPermissionService = systemPermissionService;
        this.loginConflictService = loginConflictService;
        this.fieldEncryptor = fieldEncryptor;
        this.loginSecurityService = loginSecurityService;
    }

    /**
     * 账号密码登录，优先检测登录冲突和异常设备。
     * <p>
     * 异常设备检测：查询用户最近 30 天成功登录记录，
     * 若当前 IP 和 User-Agent 均不在历史记录中（出现 &lt;3 次），
     * 则要求输入图形验证码。验证通过后继续密码校验。
     * <p>
     * 若同一账号已有在线会话，不立即踢人，而是创建一个待确认的登录冲突记录，
     * 由原会话持有者选择"接受"（让新会话登入）或"拒绝"。
     *
     * @param request 用户名 + 密码 + 可选验证码
     * @return 首次登录返回 {@link LoginResponse}，冲突时返回 {@link LoginConflictResponse}，
     *         异常设备且未提供验证码时返回 {@link RequireCaptchaResponse}
     * @throws IllegalArgumentException 账号为空 / 密码错误 / 账号已停用 / 验证码错误
     */
    public Object login(LoginRequest request) {
        String username = request == null ? null : request.getUsername();
        String password = request == null ? null : request.getPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("登录失败，账号或密码为空");
            throw new IllegalArgumentException("账号和密码不能为空");
        }

        // 提取客户端 IP 和 User-Agent，用于异常设备检测
        HttpServletRequest httpRequest = currentRequest();
        String clientIp = extractClientIp(httpRequest);
        String userAgent = httpRequest != null ? httpRequest.getHeader("User-Agent") : null;

        LoginUser loginUser = findLoginUser(username);
        if (loginUser == null) {
            log.warn("登录失败，账号或密码错误，username={}", LogMaskUtils.maskAccount(username));
            throw new IllegalArgumentException("账号或密码错误");
        }

        // 异常设备检测：从不常用 IP/UA 登录时要求图形验证码。
        // 验证码在密码校验之前执行，防止暴力破解绕过。
        boolean isFamiliar = loginSecurityService.isFamiliarDevice(loginUser.id, clientIp, userAgent);
        if (!isFamiliar) {
            if (!StringUtils.hasText(request.getCaptchaId()) || !StringUtils.hasText(request.getCaptchaCode())) {
                // 未提供验证码 → 生成验证码并返回
                CaptchaResponse captcha = loginSecurityService.generateCaptcha();
                log.info("异常设备登录，要求验证码，userId={}, ip={}, ua={}",
                        LogMaskUtils.maskId(String.valueOf(loginUser.id)), LogMaskUtils.maskIp(clientIp),
                        LogMaskUtils.maskText(userAgent));
                loginSecurityService.recordLoginAttempt(loginUser.id, loginUser.username, clientIp, userAgent,
                        "CAPTCHA_REQUIRED", null, true);
                return new RequireCaptchaResponse(captcha.getCaptchaId(), captcha.getCaptchaImage(),
                        "检测到异常登录设备，请完成图形验证码验证");
            }
            // 已提供验证码 → 校验
            if (!loginSecurityService.validateCaptcha(request.getCaptchaId(), request.getCaptchaCode())) {
                log.warn("登录失败，验证码错误，userId={}, username={}",
                        LogMaskUtils.maskId(String.valueOf(loginUser.id)), LogMaskUtils.maskAccount(username));
                loginSecurityService.recordLoginAttempt(loginUser.id, loginUser.username, clientIp, userAgent,
                        "FAIL", "验证码错误", true);
                throw new IllegalArgumentException("验证码错误，请刷新验证码后重新输入");
            }
        }

        if (!matchesPassword(password, loginUser.password)) {
            log.warn("登录失败，账号或密码错误，username={}", LogMaskUtils.maskAccount(username));
            loginSecurityService.recordLoginAttempt(loginUser.id, loginUser.username, clientIp, userAgent,
                    "FAIL", "密码错误", !isFamiliar);
            throw new IllegalArgumentException("账号或密码错误");
        }
        if (loginUser.status == null || loginUser.status != 1) {
            loginSecurityService.recordLoginAttempt(loginUser.id, loginUser.username, clientIp, userAgent,
                    "FAIL", "账号已停用", !isFamiliar);
            throw new IllegalArgumentException("账号已停用");
        }
        upgradePasswordIfNecessary(loginUser, password);
        if (StpUtil.isLogin(loginUser.id)) {
            loginSecurityService.recordLoginAttempt(loginUser.id, loginUser.username, clientIp, userAgent,
                    "PENDING", "登录冲突", !isFamiliar);
            log.info("检测到同一账号已有在线会话，创建登录冲突确认，userId={}, username={}",
                    LogMaskUtils.maskId(String.valueOf(loginUser.id)), LogMaskUtils.maskAccount(loginUser.username));
            return loginConflictService.create(loginUser.id, loginUser.username);
        }
        // 登录成功，记录到历史
        loginSecurityService.recordLoginAttempt(loginUser.id, loginUser.username, clientIp, userAgent,
                "SUCCESS", null, !isFamiliar);
        return completeLogin(loginUser);
    }

    /**
     * 执行实际登录：Sa-Token 登录 + 权限/菜单写入会话。
     * <p>
     * 设置 isConcurrent=false 确保一个账号同时只能一个会话有效。
     * 使用 getSessionByLoginId 确保与 SaPermissionConfig 读取端一致，兼容 H2 内存模式。
     */
    private LoginResponse completeLogin(LoginUser loginUser) {
        List<String> permissions = systemPermissionService.effectivePermissionCodes(loginUser.id, loginUser.roleId);
        List<MenuVO> menus = queryMenus(loginUser.roleId, permissions);
        if (menus.isEmpty()) {
            menus = defaultMenus(loginUser.roleCode);
            permissions = collectPermissions(loginUser.roleCode, menus);
        } else {
            addRelationQueryPermissions(loginUser.roleCode, permissions);
            permissions = distinctList(permissions);
        }

        // Sa-Token 会话中保存前端渲染菜单、接口鉴权和操作日志需要的最小身份信息。
        // 使用 getSessionByLoginId 可确保与 SaPermissionConfig 读取端一致，同时兼容 H2 内存模式。
        // 登录时强制单账号单会话：新 token 生效后，旧 token 会被 Sa-Token 自动踢下线。
        // 这里显式声明策略，避免环境变量误改全局配置后出现同一账号多处同时在线。
        StpUtil.login(loginUser.id, SaLoginModel.create().setIsConcurrent(false).setIsShare(false));
        StpUtil.getSessionByLoginId(loginUser.id).set("userCode", loginUser.userCode);
        StpUtil.getSessionByLoginId(loginUser.id).set("username", loginUser.username);
        StpUtil.getSessionByLoginId(loginUser.id).set("usernameMasked", LogMaskUtils.maskAccount(loginUser.username));
        StpUtil.getSessionByLoginId(loginUser.id).set("realNameMasked", LogMaskUtils.maskName(loginUser.realName));
        StpUtil.getSessionByLoginId(loginUser.id).set("roleCode", loginUser.roleCode);
        StpUtil.getSessionByLoginId(loginUser.id).set("roleName", loginUser.roleName);
        if (loginUser.customerId != null && loginUser.customerId > 0) {
            StpUtil.getSessionByLoginId(loginUser.id).set("customerId", loginUser.customerId);
        }
        StpUtil.getSessionByLoginId(loginUser.id).set("permissions", permissions);
        StpUtil.getSessionByLoginId(loginUser.id).set("menus", menus);

        String tokenValue = StpUtil.getTokenValueByLoginId(loginUser.id);
        log.info("账号登录成功，userId={}, username={}, roleCode={}",
                LogMaskUtils.maskId(String.valueOf(loginUser.id)), LogMaskUtils.maskAccount(loginUser.username), loginUser.roleCode);
        return buildResponse(loginUser, StpUtil.getTokenName(), tokenValue, permissions, menus);
    }

    /**
     * 获取当前已登录用户的完整会话信息（权限 + 菜单树）。
     * <p>
     * 用于页面刷新后前端重建路由和侧边栏。用户被删除后 Token 可能依然有效，
     * 此时提前抛异常避免后续 NPE。
     *
     * @return 包含 token、菜单、权限的登录响应
     * @throws IllegalArgumentException 用户不存在
     */
    public LoginResponse currentSession() {
        StpUtil.checkLogin();
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        LoginUser loginUser = findLoginUserById(userId);
        // 用户被删除后 Token 可能依然有效，提前抛业务异常避免后续 NPE。
        if (loginUser == null) {
            throw new IllegalArgumentException("登录用户不存在，请重新登录");
        }
        List<String> permissions = systemPermissionService.effectivePermissionCodes(userId, loginUser.roleId);
        List<MenuVO> menus = queryMenus(loginUser.roleId, permissions);
        if (menus.isEmpty()) {
            menus = defaultMenus(loginUser.roleCode);
            permissions = collectPermissions(loginUser.roleCode, menus);
        } else {
            addRelationQueryPermissions(loginUser.roleCode, permissions);
            permissions = distinctList(permissions);
        }
        // 与会话初始化保持一致的写入策略，确保 H2 等环境下读写同一会话域。
        StpUtil.getSessionByLoginId(userId).set("permissions", permissions);
        StpUtil.getSessionByLoginId(userId).set("menus", menus);
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return buildResponse(loginUser, tokenInfo.getTokenName(), tokenInfo.getTokenValue(), permissions, menus);
    }

    /**
     * 新页面轮询登录冲突状态。
     * <p>
     * 当原会话选择"接受"（status=ACCEPTED）且未过期时，新页面调用此方法完成登录。
     * 若审批人已接受或冲突已过期，自动完成登录并返回 TAKEN_OVER。
     *
     * @param conflictId 冲突 ID（由 {@link #login} 返回）
     * @return 含登录状态（ACCEPTED/TAKEN_OVER/REJECTED/EXPIRED）的响应
     */
    public LoginConflictResponse loginConflictStatus(String conflictId) {
        LoginConflictService.PendingLoginConflict conflict = loginConflictService.pending(conflictId);
        if (conflict == null) {
            return loginConflictService.status(conflictId);
        }
        // 原会话已接受时跳过超时等待，立即为新页面完成登录
        boolean accepted = "ACCEPTED".equals(conflict.getStatus());
        if (!accepted && !loginConflictService.isExpired(conflict)) {
            return loginConflictService.status(conflictId);
        }
        LoginUser loginUser = findLoginUserById(conflict.getUserId());
        if (loginUser == null || loginUser.status == null || loginUser.status != 1) {
            LoginConflictResponse response = loginConflictService.status(conflictId);
            response.setLoginStatus("REJECTED");
            response.setMessage("登录账号不存在或已停用");
            return response;
        }
        LoginResponse loginResponse = completeLogin(loginUser);
        loginConflictService.markTakenOver(conflictId);
        LoginConflictResponse response = loginConflictService.status(conflictId);
        response.setLoginStatus("TAKEN_OVER");
        response.setLoginResponse(loginResponse);
        return response;
    }

    /**
     * 查询当前登录用户的登录冲突状态。
     * 原会话页面轮询此接口判断是否有新的登录请求等待处理。
     */
    public LoginConflictResponse currentLoginConflict() {
        StpUtil.checkLogin();
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        return loginConflictService.current(userId);
    }

    /**
     * 拒绝新的登录请求。
     *
     * @param conflictId 冲突 ID
     * @return 含 REJECTED 状态的冲突响应
     */
    public LoginConflictResponse rejectLoginConflict(String conflictId) {
        StpUtil.checkLogin();
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        return loginConflictService.reject(conflictId, userId);
    }

    /**
     * 原会话接受新的登录请求，只标记状态为 ACCEPTED，不在此处完成登录。
     * <p>
     * 这样设计是为了不破坏当前会话的 token——新页面通过轮询
     * {@link #loginConflictStatus} 检测到 ACCEPTED 后自行完成登录。
     *
     * @param conflictId 冲突 ID
     * @return 含 ACCEPTED 状态的冲突响应
     */
    public LoginConflictResponse acceptLoginConflict(String conflictId) {
        StpUtil.checkLogin();
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        LoginConflictService.PendingLoginConflict conflict = loginConflictService.pending(conflictId);
        if (conflict == null || !userId.equals(conflict.getUserId())) {
            LoginConflictResponse response = loginConflictService.status(conflictId);
            response.setLoginStatus("REJECTED");
            response.setMessage("登录确认请求不存在或无权处理");
            return response;
        }
        // 仅标记为已接受，不在此处进行 completeLogin（避免影响当前会话的 token）。
        // 新页面轮询 loginConflictStatus 检测到 ACCEPTED 后会自行完成登录。
        loginConflictService.accept(conflictId);
        LoginConflictResponse response = loginConflictService.status(conflictId);
        response.setLoginStatus("ACCEPTED");
        response.setMessage("已允许新的登录请求");
        return response;
    }

    /**
     * 登出当前账号。
     * 使用 logout(loginId) 同时清除 TokenSession 和 AccountSession，
     * 防止重新登录时误判为冲突。
     */
    public void logout() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            StpUtil.logout();
            return;
        }
        String usernameMasked = String.valueOf(StpUtil.getSessionByLoginId(loginId).get("usernameMasked", ""));
        log.info("账号退出登录，userId={}, username={}", LogMaskUtils.maskId(String.valueOf(loginId)), usernameMasked);
        // logout(loginId) 同时清除 TokenSession 和 AccountSession，防止重新登录时误判为冲突。
        StpUtil.logout(loginId);
    }

    private LoginResponse buildResponse(LoginUser loginUser, String tokenName, String tokenValue,
                                        List<String> permissions, List<MenuVO> menus) {
        if (loginUser == null) {
            throw new IllegalArgumentException("登录用户不存在");
        }
        return new LoginResponse(
                loginUser.username,
                loginUser.id,
                tokenName,
                tokenValue,
                loginUser.id,
                loginUser.userCode,
                LogMaskUtils.maskAccount(loginUser.username),
                LogMaskUtils.maskName(loginUser.realName),
                loginUser.roleCode,
                loginUser.roleName,
                permissions,
                menus
        );
    }

    /**
     * 修改个人资料（姓名/手机/邮箱）。
     * <p>
     * 手机号通过 {@link FieldEncryptor} 加密入库，至少填写一项。
     *
     * @throws IllegalArgumentException 格式校验不通过
     */
    public void updateProfile(ProfileUpdateRequest request) {
        StpUtil.checkLogin();
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        if (!StringUtils.hasText(request.getRealName()) && !StringUtils.hasText(request.getMobile()) && !StringUtils.hasText(request.getEmail())) {
            throw new IllegalArgumentException("至少需要填写一项信息");
        }
        if (StringUtils.hasText(request.getMobile()) && !request.getMobile().trim().matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("手机号必须是11位中国大陆手机号");
        }
        authMapper.updateProfile(userId, request.getRealName(),
                fieldEncryptor.encrypt(request.getMobile()), request.getEmail());
    }

    /**
     * 修改密码。
     * <p>
     * 需旧密码验证，新密码 BCrypt 入库后强制退出当前会话，
     * 要求用户用新密码重新登录。
     *
     * @throws IllegalArgumentException 原密码错误
     */
    public void changePassword(PasswordChangeRequest request) {
        StpUtil.checkLogin();
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        Map<String, Object> user = authMapper.findLoginUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        String storedPassword = toString(user.get("password"));
        if (!matchesPassword(request.getOldPassword(), storedPassword)) {
            throw new IllegalArgumentException("原密码错误");
        }
        authMapper.updatePassword(userId, PASSWORD_ENCODER.encode(request.getNewPassword()));
        StpUtil.logout(userId);
    }

    /**
     * 查找登录用户（含密码字段），兼容明文密码升级为 BCrypt。
     * <p>
     * 明文密码登录成功后自动调用 upgradePasswordIfNecessary 升级。
     *
     * @param username 登录账号
     * @return 用户信息，不存在时 null
     */
    private LoginUser findLoginUser(String username) {
        return mapUser(authMapper.findLoginUserByUsername(username));
    }

    /** 根据 userId 查找用户信息 */
    private LoginUser findLoginUserById(Long userId) {
        return mapUser(authMapper.findLoginUserById(userId));
    }

    /**
     * 密码匹配：优先 BCrypt 编码匹配，兼容旧数据明文密码。
     *
     * @param rawPassword 用户输入的明文密码
     * @param storedPassword 数据库中存储的密码（明文或 BCrypt）
     * @return 匹配成功返回 true
     */
    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (!StringUtils.hasText(storedPassword)) {
            return false;
        }
        // 兼容旧数据中的明文密码，登录成功后会在 upgradePasswordIfNecessary 中升级为 BCrypt。
        if (isBcrypt(storedPassword)) {
            return PASSWORD_ENCODER.matches(rawPassword, storedPassword);
        }
        return rawPassword.equals(storedPassword);
    }

    /**
     * 如果存储的是明文密码，升级为 BCrypt 编码。
     * <p>
     * 升级后更新数据库并刷新内存中的 password 字段，避免后续 isBcrypt 重复判断。
     */
    private void upgradePasswordIfNecessary(LoginUser loginUser, String rawPassword) {
        if (isBcrypt(loginUser.password)) {
            return;
        }
        String encoded = PASSWORD_ENCODER.encode(rawPassword);
        authMapper.updatePassword(loginUser.id, encoded);
        loginUser.password = encoded;
        log.info("账号密码已升级为 BCrypt，userId={}, username={}", LogMaskUtils.maskId(String.valueOf(loginUser.id)), LogMaskUtils.maskAccount(loginUser.username));
    }

    /** 判断密码是否为 BCrypt 编码（$2a$/$2b$/$2y$ 开头） */
    private boolean isBcrypt(String password) {
        return password != null && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }

    /**
     * 将数据库查询结果 Map 映射为内部 LoginUser 对象。
     * <p>
     * 使用 toLong/toInteger/toString 安全转换，null-safe。
     */
    private LoginUser mapUser(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        LoginUser user = new LoginUser();
        user.id = toLong(row.get("id"));
        user.userCode = toString(row.get("userCode"));
        user.username = toString(row.get("username"));
        user.realName = toString(row.get("realName"));
        user.password = toString(row.get("password"));
        user.status = toInteger(row.get("status"));
        user.roleId = toLong(row.get("roleId"));
        user.roleCode = toString(row.get("roleCode"));
        user.roleName = toString(row.get("roleName"));
        user.customerId = toLong(row.get("customerId"));
        return user;
    }

    /**
     * 查询并组装角色对应的菜单树。
     * <p>
     * 合并 sys_role_menu 直接分配的菜单和通过权限码匹配的菜单，
     * 按 parentId 递归挂载为树结构，供前端侧边栏渲染。
     */
    private List<MenuVO> queryMenus(Long roleId, List<String> permissions) {
        Map<Long, MenuVO> mergedRows = new LinkedHashMap<>();
        for (MenuVO menu : authMapper.selectMenusByRoleId(roleId)) {
            if (canShowMenu(menu, permissions)) {
                mergedRows.put(menu.getId(), menu);
            }
        }
        for (MenuVO menu : authMapper.selectMenusByPermissionCodes(menuLookupPermissionCodes(permissions))) {
            if (canShowMenu(menu, permissions)) {
                mergedRows.put(menu.getId(), menu);
            }
        }
        List<MenuVO> rows = new ArrayList<>(mergedRows.values());
        Map<Long, MenuVO> byId = new LinkedHashMap<>();
        rows.forEach(menu -> byId.put(menu.getId(), menu));
        List<MenuVO> roots = new ArrayList<>();
        // 数据库按扁平菜单存储，这里按 parent_id 组装成前端侧边栏需要的树形结构。
        for (MenuVO menu : rows) {
            if (menu.getParentId() != null && menu.getParentId() != 0 && byId.containsKey(menu.getParentId())) {
                byId.get(menu.getParentId()).getChildren().add(menu);
            } else {
                roots.add(menu);
            }
        }
        return roots;
    }

    /**
     * 从权限列表中提取模块前缀，生成 :manage 和 :view 两类通配权限码，
     * 用于匹配未直接分配但模块相关的菜单。
     */
    private List<String> menuLookupPermissionCodes(List<String> permissions) {
        LinkedHashSet<String> codes = new LinkedHashSet<>(permissions);
        for (String permission : permissions) {
            String module = moduleFromPermission(permission);
            codes.add(module + ":manage");
            codes.add(module + ":view");
        }
        return new ArrayList<>(codes);
    }

    /**
     * 判断菜单是否应对当前用户显示。
     * <p>
     * 无 permissionCode 的菜单直接显示；有 permissionCode 时检查权限列表中
     * 是否精确匹配或模块前缀匹配。
     */
    private boolean canShowMenu(MenuVO menu, List<String> permissions) {
        if (menu == null || !StringUtils.hasText(menu.getPermissionCode())) {
            return true;
        }
        if (permissions.contains(menu.getPermissionCode())) {
            return true;
        }
        String module = moduleFromPermission(menu.getPermissionCode());
        String prefix = module + ":";
        for (String permission : permissions) {
            if (permission.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 列表去重，保持顺序 */
    private List<String> distinctList(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (!result.contains(s)) {
                result.add(s);
            }
        }
        return result;
    }

    private String moduleFromPermission(String permissionCode) {
        int index = permissionCode.lastIndexOf(':');
        return index < 0 ? permissionCode : permissionCode.substring(0, index);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger) {
            return ((BigInteger) value).longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<MenuVO> mergeDefaultMenus(String roleCode, List<MenuVO> dbMenus) {
        Map<String, MenuVO> merged = new LinkedHashMap<>();
        // 默认菜单只作为兜底；如果数据库里配置了额外菜单，会在后面追加进来。
        for (MenuVO menu : defaultMenus(roleCode)) {
            merged.put(menu.getPath(), menu);
        }
        for (MenuVO menu : dbMenus) {
            if (StringUtils.hasText(menu.getPath()) && !merged.containsKey(menu.getPath())) {
                merged.put(menu.getPath(), menu);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<String> collectPermissions(String roleCode, List<MenuVO> menus) {
        List<String> permissions = new ArrayList<>();
        for (MenuVO menu : flattenMenus(menus)) {
            if (StringUtils.hasText(menu.getPermissionCode()) && !permissions.contains(menu.getPermissionCode())) {
                permissions.add(menu.getPermissionCode());
            }
        }
        List<String> expanded = new ArrayList<>(permissions);
        for (String permission : permissions) {
            expandActionPermissions(permission, expanded);
        }
        // 部分角色页面需要加载关联下拉数据，只补充只读查询权限，不额外开放对应菜单。
        addRelationQueryPermissions(roleCode, expanded);
        return distinctList(expanded);
    }

    private void addRelationQueryPermissions(String roleCode, List<String> expanded) {
        if ("OPERATIONS_MANAGER".equals(roleCode)) {
            addQueryPermissions(expanded, "customer", "driver", "vehicle", "fee");
            return;
        }
        if ("ORDER_OPERATOR".equals(roleCode) || "CUSTOMER_SERVICE".equals(roleCode)) {
            addQueryPermissions(expanded, "customer", "order", "waybill", "track");
            return;
        }
        if ("DISPATCHER".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "waybill");
            return;
        }
        if ("FLEET_MANAGER".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "waybill", "dispatch", "driver", "vehicle");
            return;
        }
        if ("DRIVER".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "waybill", "dispatch", "driver", "vehicle");
            return;
        }
        if ("EXCEPTION_HANDLER".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "task", "track");
            return;
        }
        if ("FINANCE".equals(roleCode)) {
            addQueryPermissions(expanded, "order");
            return;
        }
        if ("FINANCE_MANAGER".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "fee");
            return;
        }
        if ("AUDITOR".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "waybill", "track", "fee", "system:log");
        }
    }

    private void addQueryPermissions(List<String> expanded, String... prefixes) {
        for (String prefix : prefixes) {
            expanded.add(prefix + ":query");
        }
    }

    private void expandActionPermissions(String permission, List<String> expanded) {
        if (permission.endsWith(":manage")) {
            String prefix = permission.substring(0, permission.length() - ":manage".length());
            // manage 表示该模块完整管理权限，展开成前端按钮和后端接口都会使用的细粒度动作权限。
            expanded.add(prefix + ":query");
            expanded.add(prefix + ":create");
            expanded.add(prefix + ":update");
            expanded.add(prefix + ":delete");
            expanded.add(prefix + ":export");
            expanded.add(prefix + ":import");
            return;
        }
        if (permission.endsWith(":view")) {
            String prefix = permission.substring(0, permission.length() - ":view".length());
            expanded.add(prefix + ":query");
            expanded.add(prefix + ":export");
        }
    }

    private List<MenuVO> flattenMenus(List<MenuVO> menus) {
        List<MenuVO> flattened = new ArrayList<>();
        for (MenuVO menu : menus) {
            flattened.add(menu);
            if (menu.getChildren() != null && !menu.getChildren().isEmpty()) {
                flattened.addAll(flattenMenus(menu.getChildren()));
            }
        }
        return flattened;
    }

    private List<MenuVO> defaultMenus(String roleCode) {
        if ("CUSTOMER_SERVICE".equals(roleCode)) {
            return menus("customers", "orders", "waybills", "tracks");
        }
        if ("ORDER_OPERATOR".equals(roleCode)) {
            return menus("orders", "customers", "waybills", "tracks");
        }
        if ("DISPATCHER".equals(roleCode)) {
            return menus("dispatches", "tasks", "drivers", "vehicles", "tracks", "exceptions");
        }
        if ("FLEET_MANAGER".equals(roleCode)) {
            return menus("drivers", "vehicles", "dispatches", "tasks", "tracks");
        }
        if ("DRIVER".equals(roleCode)) {
            return menus("tasks", "tracks", "exceptions");
        }
        if ("EXCEPTION_HANDLER".equals(roleCode)) {
            return menus("exceptions", "orders", "tasks", "tracks");
        }
        if ("FINANCE".equals(roleCode)) {
            return menus("fees", "dashboard");
        }
        if ("FINANCE_MANAGER".equals(roleCode)) {
            return menus("fees", "dashboard", "system", "operationLogs");
        }
        if ("OPERATIONS_MANAGER".equals(roleCode)) {
            return menus("dashboard", "orders", "waybills", "dispatches", "tasks", "tracks", "exceptions");
        }
        if ("AUDITOR".equals(roleCode)) {
            return menus("dashboard", "orders", "waybills", "tracks", "fees", "system", "operationLogs");
        }
        if ("FILE_MANAGER".equals(roleCode)) {
            return menus("files", "resources");
        }
        if ("CUSTOMER".equals(roleCode)) {
            return menus("orders", "tracks");
        }
        return menus("dashboard", "orders", "customers", "waybills", "dispatches", "tasks", "tracks",
                "drivers", "vehicles", "exceptions", "fees", "system", "users", "roles", "permissions", "files", "resources");
    }

    private List<MenuVO> menus(String... keys) {
        Map<String, MenuVO> all = allDefaultMenus();
        List<MenuVO> result = new ArrayList<>();
        for (String key : keys) {
            MenuVO menu = all.get(key);
            if (menu != null) {
                result.add(menu);
            }
        }
        return result;
    }

    /**
     * 从当前请求中获取 HttpServletRequest。
     * <p>用于提取客户端 IP 和 User-Agent，供异常设备检测使用。
     */
    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return null;
        }
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }

    /**
     * 提取客户端真实 IP（优先代理头，兜底 RemoteAddr）。
     * <p>与 OperationLogInterceptor 保持一致的多级代理穿透逻辑。
     */
    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.trim().isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private Map<String, MenuVO> allDefaultMenus() {
        Map<String, MenuVO> menus = new LinkedHashMap<>();
        menus.put("dashboard", menu(-1L, 0L, "运营看板", "/dashboard", "dashboard:view", 10));
        menus.put("orders", menu(-2L, 0L, "运单管理", "/orders", "order:manage", 20));
        menus.put("customers", menu(-3L, 0L, "客户管理", "/customers", "customer:manage", 30));
        menus.put("waybills", menu(-4L, 0L, "运单中心", "/waybills", "waybill:manage", 40));
        menus.put("dispatches", menu(-5L, 0L, "调度管理", "/dispatches", "dispatch:manage", 50));
        menus.put("tasks", menu(-6L, 0L, "运输任务", "/tasks", "task:manage", 60));
        menus.put("tracks", menu(-7L, 0L, "物流轨迹", "/tracks", "track:view", 70));
        menus.put("drivers", menu(-8L, 0L, "司机管理", "/drivers", "driver:manage", 80));
        menus.put("vehicles", menu(-9L, 0L, "车辆管理", "/vehicles", "vehicle:manage", 90));
        menus.put("exceptions", menu(-10L, 0L, "异常管理", "/exceptions", "exception:manage", 100));
        menus.put("fees", menu(-11L, 0L, "费用结算", "/fees", "fee:manage", 110));
        MenuVO system = menu(-12L, 0L, "系统管理", "/system", "system:manage", 900);
        system.setChildren(Arrays.asList(
                menu(-13L, -12L, "用户管理", "/system/users", "system:user:manage", 910),
                menu(-14L, -12L, "角色管理", "/system/roles", "system:role:manage", 920),
                menu(-18L, -12L, "权限配置", "/system/permissions", "system:permission:manage", 925),
                menu(-15L, -12L, "操作日志", "/system/operation-logs", "system:log:view", 930)
        ));
        menus.put("system", system);
        menus.put("users", system.getChildren().get(0));
        menus.put("roles", system.getChildren().get(1));
        menus.put("permissions", system.getChildren().get(2));
        menus.put("operationLogs", system.getChildren().get(3));
        menus.put("files", menu(-16L, 0L, "上传文件", "/files", "file:manage", 940));
        menus.put("resources", menu(-17L, 0L, "资源中心", "/resources", "resource:view", 950));
        return menus;
    }

    private MenuVO menu(Long id, Long parentId, String name, String path, String permissionCode, Integer sortNo) {
        MenuVO menu = new MenuVO();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setName(name);
        menu.setPath(path);
        menu.setPermissionCode(permissionCode);
        menu.setSortNo(sortNo);
        return menu;
    }

    private static class LoginUser {
        private Long id;
        private String userCode;
        private String username;
        private String realName;
        private String password;
        private Integer status;
        private Long roleId;
        private String roleCode;
        private String roleName;
        private Long customerId;
    }
}
