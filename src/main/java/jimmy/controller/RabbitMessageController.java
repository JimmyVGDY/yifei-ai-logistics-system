package jimmy.controller;

import jimmy.model.ApiResponse;
import jimmy.model.InfrastructureStatus;
import jimmy.service.RabbitMessageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rabbitmq")
public class RabbitMessageController {

    private final RabbitMessageService rabbitMessageService;

    public RabbitMessageController(RabbitMessageService rabbitMessageService) {
        this.rabbitMessageService = rabbitMessageService;
    }

    @PostMapping("/messages")
    public ApiResponse<InfrastructureStatus> send(@RequestParam String message) {
        rabbitMessageService.sendDemoMessage(message);
        return ApiResponse.success(InfrastructureStatus.of("rabbitmq", "sent")
                .detail("message", message));
    }
}
