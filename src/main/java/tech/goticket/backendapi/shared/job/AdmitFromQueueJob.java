package tech.goticket.backendapi.shared.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.goticket.backendapi.waitingroom.service.WaitingRoomService;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdmitFromQueueJob {

    private final StringRedisTemplate redis;
    private final WaitingRoomService waitingRoomService;

    @Value("${goticket.waitingroom.admit-batch-size:100}")
    private int admitBatchSize;

    @Scheduled(fixedDelay = 5_000, initialDelay = 5_000)
    public void admit() {
        Set<String> activeEvents = redis.opsForSet().members("waitingroom:active-events");
        if (activeEvents == null || activeEvents.isEmpty()) return;

        for (String eventIdString : activeEvents) {
            try {
                Long eventId = Long.parseLong(eventIdString);
                waitingRoomService.admitBatch(eventId, admitBatchSize);
            }
            catch (Exception e) {
                log.error("AdmitFromQueueJob: falha no evento {}: {}", eventIdString, e.getMessage(), e);
            }
        }
    }
}
