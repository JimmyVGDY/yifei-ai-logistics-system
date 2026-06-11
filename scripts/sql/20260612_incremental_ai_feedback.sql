-- AI feedback table: records user thumb-up/down on AI responses
create table if not exists ai_message_feedback (
    id bigint primary key comment 'snowflake ID',
    message_id varchar(64) not null comment 'AI message ID',
    conversation_id varchar(64) not null comment 'AI conversation ID',
    user_id varchar(64) not null comment 'feedback user ID',
    rating varchar(8) not null comment 'UP or DOWN',
    comment varchar(500) null comment 'optional feedback comment',
    created_at timestamp not null default current_timestamp,
    deleted tinyint not null default 0,
    version int not null default 0,
    index idx_feedback_message (message_id),
    index idx_feedback_conversation (conversation_id),
    index idx_feedback_rating (rating, created_at),
    index idx_feedback_created (created_at)
) comment='AI message feedback table';
