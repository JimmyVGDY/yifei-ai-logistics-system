"""核心 AI 能力：模型网关、Agent 编排、意图分类、Prompt 渲染、幻觉检查。"""

from ai_service.core.model_gateway import ModelGateway, ProviderRegistry, ModelResponse
from ai_service.core.prompt_engine import PromptEngine, PromptRenderResult
from ai_service.core.agent import AgentOrchestrator, AgentContext, ToolCall, ToolResult
