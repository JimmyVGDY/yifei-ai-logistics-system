package jimmy.auth.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.auth.entity.LoginHistory;
import jimmy.auth.mapper.LoginHistoryMapper;
import jimmy.auth.model.CaptchaResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 登录安全服务 —— 设备熟悉度检查 + 图形验证码生成与校验。
 * <p>
 * 核心逻辑：
 * <ol>
 *   <li>用户登录时，查询其最近 30 天成功登录的 IP 和 User-Agent</li>
 *   <li>如果当前 IP/UA 在历史记录中出现 ≥3 次，视为"常用设备"，跳过验证码</li>
 *   <li>否则视为"异常设备"，要求输入图形验证码</li>
 *   <li>验证码答案存储在 Redis 中，TTL 5 分钟</li>
 * </ol>
 *
 * <p>验证码使用 Java AWT 绘制简单的四则运算（如 3+5=?），返回 Base64 PNG 图片。
 * 不依赖第三方验证码库，纯 JDK 实现。
 */
@Service
public class LoginSecurityService {

    /** 设备熟悉度检查的时间窗口（天） */
    private static final int FAMILIAR_WINDOW_DAYS = 30;
    /** 同一 IP/UA 被认定为"常用设备"的最小成功登录次数 */
    private static final int FAMILIAR_MIN_COUNT = 3;
    /** 验证码 Redis Key 前缀 */
    private static final String CAPTCHA_KEY_PREFIX = "login:captcha:";
    private static final String FAILURE_KEY_PREFIX = "login:failure:";
    private static final String LOCK_KEY_PREFIX = "login:lock:";
    private static final String RATE_KEY_PREFIX = "login:rate:";
    /** 验证码有效期（分钟） */
    private static final int CAPTCHA_TTL_MINUTES = 5;
    /** 验证码图片宽度 */
    private static final int CAPTCHA_WIDTH = 160;
    /** 验证码图片高度 */
    private static final int CAPTCHA_HEIGHT = 50;

    /** 验证码颜色池：用于绘制干扰线和噪点，避免简单的灰度图被 OCR 识别 */
    private static final Color[] DISTRACT_COLORS = {
            new Color(50, 120, 200),
            new Color(180, 80, 50),
            new Color(30, 160, 80),
            new Color(140, 60, 180),
            new Color(220, 140, 30),
    };

    private final LoginHistoryMapper loginHistoryMapper;
    private final StringRedisTemplate redisTemplate;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final int maxFailures;
    private final int lockMinutes;
    private final int rateLimitPerMinute;

    public LoginSecurityService(LoginHistoryMapper loginHistoryMapper,
                                StringRedisTemplate redisTemplate,
                                CompactSnowflakeIdGenerator idGenerator,
                                @Value("${app.auth.login.max-failures:5}") int maxFailures,
                                @Value("${app.auth.login.lock-minutes:15}") int lockMinutes,
                                @Value("${app.auth.login.rate-limit-per-minute:10}") int rateLimitPerMinute) {
        this.loginHistoryMapper = loginHistoryMapper;
        this.redisTemplate = redisTemplate;
        this.idGenerator = idGenerator;
        this.maxFailures = Math.max(1, maxFailures);
        this.lockMinutes = Math.max(1, lockMinutes);
        this.rateLimitPerMinute = Math.max(1, rateLimitPerMinute);
    }

    public void assertLoginAllowed(String username, String clientIp) {
        String accountKey = normalized(username);
        String ipKey = normalized(clientIp);
        if (isLocked("account", accountKey) || isLocked("ip", ipKey)) {
            throw new IllegalArgumentException("登录失败次数过多，请稍后再试");
        }
        long accountRate = incrementWithTtl(RATE_KEY_PREFIX + "account:" + accountKey, 1, TimeUnit.MINUTES);
        long ipRate = incrementWithTtl(RATE_KEY_PREFIX + "ip:" + ipKey, 1, TimeUnit.MINUTES);
        if (accountRate > rateLimitPerMinute || ipRate > rateLimitPerMinute) {
            lock("account", accountKey);
            lock("ip", ipKey);
            throw new IllegalArgumentException("登录请求过于频繁，请稍后再试");
        }
    }

    public void recordLoginFailure(String username, String clientIp) {
        String accountKey = normalized(username);
        String ipKey = normalized(clientIp);
        long accountFailures = incrementWithTtl(FAILURE_KEY_PREFIX + "account:" + accountKey, lockMinutes, TimeUnit.MINUTES);
        long ipFailures = incrementWithTtl(FAILURE_KEY_PREFIX + "ip:" + ipKey, lockMinutes, TimeUnit.MINUTES);
        if (accountFailures >= maxFailures) {
            lock("account", accountKey);
        }
        if (ipFailures >= maxFailures) {
            lock("ip", ipKey);
        }
    }

    public void clearLoginFailures(String username, String clientIp) {
        String accountKey = normalized(username);
        String ipKey = normalized(clientIp);
        redisTemplate.delete(FAILURE_KEY_PREFIX + "account:" + accountKey);
        redisTemplate.delete(FAILURE_KEY_PREFIX + "ip:" + ipKey);
        redisTemplate.delete(LOCK_KEY_PREFIX + "account:" + accountKey);
        redisTemplate.delete(LOCK_KEY_PREFIX + "ip:" + ipKey);
    }

    /**
     * 检查当前登录设备是否属于该用户的常用设备。
     * <p>
     * 查询最近 30 天成功登录记录，统计同一 IP 和同一 UA（归一化）的出现次数。
     * 任一指标达到 FAMILIAR_MIN_COUNT 即视为常用设备。
     *
     * @param userId    用户ID
     * @param clientIp  当前登录 IP
     * @param userAgent 当前登录 User-Agent
     * @return true=常用设备无需验证码，false=异常设备需要验证码
     */
    public boolean isFamiliarDevice(Long userId, String clientIp, String userAgent) {
        if (userId == null) {
            return false;
        }
        List<LoginHistory> recentLogins = loginHistoryMapper.selectRecentSuccessLogins(userId, FAMILIAR_WINDOW_DAYS);
        if (recentLogins == null || recentLogins.isEmpty()) {
            // 无历史登录记录，视为新设备
            return false;
        }
        // 统计同一 IP 的出现次数
        int ipCount = 0;
        // 统计相似 UA 的出现次数（取前 100 字符做归一化，忽略版本号差异）
        int uaCount = 0;
        String normalizedUa = normalizeUserAgent(userAgent);
        for (int i = 0; i < recentLogins.size(); i++) {
            LoginHistory history = recentLogins.get(i);
            if (clientIp != null && clientIp.equals(history.getLoginIp())) {
                ipCount++;
            }
            String historyUa = normalizeUserAgent(history.getUserAgent());
            if (normalizedUa != null && normalizedUa.equals(historyUa)) {
                uaCount++;
            }
        }
        return ipCount >= FAMILIAR_MIN_COUNT || uaCount >= FAMILIAR_MIN_COUNT;
    }

    /**
     * 生成图形验证码（数学四则运算）。
     * <p>
     * 使用 Java AWT 绘制算式图片，答案存入 Redis（TTL 5 分钟）。
     * 前端回传 captchaId + captchaCode 后通过 {@link #validateCaptcha} 校验。
     *
     * @return 含 captchaId 和 Base64 图片的响应
     */
    public CaptchaResponse generateCaptcha() {
        // 随机生成两位数以内的加法或乘法（减法/除法对用户不够友好）
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int a = random.nextInt(1, 50);
        int b = random.nextInt(1, 50);
        boolean isMultiply = random.nextBoolean();
        String expression;
        int answer;
        if (isMultiply) {
            // 乘法时将因子降至个位数，避免心算困难
            a = random.nextInt(1, 10);
            b = random.nextInt(1, 10);
            expression = a + " × " + b + " = ?";
            answer = a * b;
        } else {
            expression = a + " + " + b + " = ?";
            answer = a + b;
        }

        // 生成验证码 ID，将答案存入 Redis
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + captchaId, String.valueOf(answer),
                CAPTCHA_TTL_MINUTES, TimeUnit.MINUTES);

        // 绘制验证码图片
        String base64Image = drawCaptchaImage(expression);
        return new CaptchaResponse(captchaId, base64Image);
    }

    /**
     * 校验用户输入的验证码。
     * <p>
     * 验证成功后删除 Redis 中的答案，防止重复使用。
     *
     * @param captchaId   验证码唯一标识
     * @param captchaCode 用户输入的答案
     * @return true=验证通过
     */
    public boolean validateCaptcha(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null) {
            return false;
        }
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        String expected = redisTemplate.opsForValue().get(key);
        if (expected == null) {
            return false;
        }
        if (expected.equals(captchaCode.trim())) {
            // 验证通过后立即删除，防止重放攻击
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }

    /**
     * 记录登录尝试到 sys_login_history 表。
     * <p>
     * 无论成功/失败/需验证码，均记录以备后续分析。
     *
     * @param userId         用户ID
     * @param username       用户名
     * @param loginIp        客户端IP
     * @param userAgent      客户端标识
     * @param loginResult    登录结果
     * @param failReason     失败原因（成功时为 null）
     * @param requireCaptcha 是否触发了验证码检查
     */
    public void recordLoginAttempt(Long userId, String username, String loginIp, String userAgent,
                                   String loginResult, String failReason, boolean requireCaptcha) {
        LoginHistory history = new LoginHistory();
        history.setId(idGenerator.nextId());
        history.setUserId(userId);
        history.setUsername(username);
        history.setLoginIp(loginIp);
        history.setUserAgent(truncateUserAgent(userAgent));
        history.setLoginResult(loginResult);
        history.setFailReason(failReason);
        history.setRequireCaptcha(requireCaptcha ? 1 : 0);
        history.setLoginTime(new Timestamp(System.currentTimeMillis()));
        loginHistoryMapper.insert(history);
    }

    /**
     * User-Agent 归一化：截取前 100 字符，提取浏览器/操作系统核心标识。
     * <p>
     * 忽略版本号的微小差异——同一设备的 UA 版本号随着更新时间变化，
     * 但浏览器家族和 OS 保持稳定。
     */
    private String normalizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return null;
        }
        // 截取前 100 字符做归一化匹配
        String trimmed = userAgent.length() > 100 ? userAgent.substring(0, 100) : userAgent;
        // 移除版本号（x.x.x 格式），但保留浏览器和 OS 标识
        return trimmed.replaceAll("\\d+\\.\\d+(\\.\\d+)?", "X");
    }

    /** User-Agent 入库前截断，避免超长字段导致 SQL 异常 */
    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }

    private boolean isLocked(String dimension, String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_KEY_PREFIX + dimension + ":" + key));
    }

    private void lock(String dimension, String key) {
        redisTemplate.opsForValue().set(LOCK_KEY_PREFIX + dimension + ":" + key, "1", lockMinutes, TimeUnit.MINUTES);
    }

    private long incrementWithTtl(String key, long ttl, TimeUnit unit) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, ttl, unit);
        }
        return value == null ? 0L : value;
    }

    private String normalized(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }

    /**
     * 使用 Java AWT 绘制验证码图片。
     * <p>
     * 纯 JDK 实现，不依赖第三方库。
     * 包含干扰线、噪点、旋转字符等反 OCR 措施。
     *
     * @param text 要绘制的算式文本（如 "3 + 5 = ?"）
     * @return Base64 编码的 PNG 图片字符串
     */
    private String drawCaptchaImage(String text) {
        BufferedImage image = new BufferedImage(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // 抗锯齿渲染
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 白色背景
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT);

        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 绘制 5 条随机干扰线（增加 OCR 难度）
        for (int i = 0; i < 5; i++) {
            g2d.setColor(DISTRACT_COLORS[random.nextInt(DISTRACT_COLORS.length)]);
            int x1 = random.nextInt(CAPTCHA_WIDTH);
            int y1 = random.nextInt(CAPTCHA_HEIGHT);
            int x2 = random.nextInt(CAPTCHA_WIDTH);
            int y2 = random.nextInt(CAPTCHA_HEIGHT);
            g2d.setStroke(new BasicStroke(1.2f));
            g2d.drawLine(x1, y1, x2, y2);
        }

        // 绘制 30 个随机噪点
        for (int i = 0; i < 30; i++) {
            g2d.setColor(DISTRACT_COLORS[random.nextInt(DISTRACT_COLORS.length)]);
            int x = random.nextInt(CAPTCHA_WIDTH);
            int y = random.nextInt(CAPTCHA_HEIGHT);
            g2d.fillRect(x, y, 2, 2);
        }

        // 逐个字符绘制，支持旋转和颜色变化
        Font baseFont = new Font("SansSerif", Font.BOLD, 28);
        int charSpacing = CAPTCHA_WIDTH / (text.length() + 1);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            // 每个字符随机旋转 -15° ~ 15°
            double angle = Math.toRadians(random.nextDouble() * 30 - 15);
            int x = charSpacing * (i + 1) - 10;
            int y = (CAPTCHA_HEIGHT / 2) + 12;

            // 随机字符颜色
            g2d.setColor(DISTRACT_COLORS[random.nextInt(DISTRACT_COLORS.length)]);

            // 应用旋转
            java.awt.geom.AffineTransform originalTransform = g2d.getTransform();
            g2d.rotate(angle, x, y);
            g2d.setFont(baseFont);
            g2d.drawString(String.valueOf(ch), x, y);
            g2d.setTransform(originalTransform);
        }

        g2d.dispose();

        // 编码为 Base64 PNG
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            // 绘图 I/O 异常几乎不可能发生（内存流），兜底返回纯文本算式
            throw new RuntimeException("验证码图片生成失败", e);
        }
    }
}
