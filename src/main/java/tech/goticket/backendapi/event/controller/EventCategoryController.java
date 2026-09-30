package tech.goticket.backendapi.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.goticket.backendapi.event.dto.EventCategoryDTO;
import tech.goticket.backendapi.event.service.EventCategoryService;

import java.util.List;

@RestController
@RequestMapping("/event-categories")
@RequiredArgsConstructor
@Tag(name = "Categorias de evento", description = "Categorias para classificação e filtro de eventos")
public class EventCategoryController {

    private final EventCategoryService eventCategoryService;

    @GetMapping
    @Operation(summary = "Lista as categorias de evento")
    public ResponseEntity<List<EventCategoryDTO>> getAllEventCategories() {
        var eventCategories = eventCategoryService.findAllCategories();
        return ResponseEntity.ok(eventCategories);
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "Detalha uma categoria de evento")
    public ResponseEntity<EventCategoryDTO> getEventCategoryById(@PathVariable Long categoryId) {
        EventCategoryDTO category = eventCategoryService.findCategoryById(categoryId);

        return ResponseEntity.ok(category);
    }
}
