-- 用途：AI RAG 文档增量索引状态表。
-- 说明：可重复执行；只新增索引元数据表，不清空、不重建任何现有业务数据。

create table if not exists ai_document_index (
    id bigint primary key comment '短位随机主键',
    source_path varchar(512) not null comment '文档相对路径，例如 README.md 或 docs/spring-ai.md',
    file_name varchar(255) not null comment '文档文件名',
    content_hash varchar(128) not null comment '文档内容 SHA-256 哈希，用于判断是否需要重建向量',
    chunk_count int not null default 0 comment '最近一次成功索引的分块数量',
    status varchar(32) not null default 'SUCCESS' comment '索引状态：SUCCESS/FAILED',
    error_message varchar(1000) null comment '索引失败时的脱敏错误摘要',
    indexed_at datetime null comment '最近一次成功索引时间',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0 comment '逻辑删除标记',
    unique key uk_ai_document_index_source (source_path),
    key idx_ai_document_index_status_time (status, updated_at),
    key idx_ai_document_index_hash (content_hash)
) comment='AI RAG文档索引状态表';
