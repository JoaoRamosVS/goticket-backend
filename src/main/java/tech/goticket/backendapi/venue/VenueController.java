package tech.goticket.backendapi.venue;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.goticket.backendapi.organizer.Organizer;
import tech.goticket.backendapi.organizer.OrganizerService;
import tech.goticket.backendapi.shared.exception.InvalidArgumentException;
import tech.goticket.backendapi.shared.exception.ResourceNotFoundException;
import tech.goticket.backendapi.shared.exception.user.DocumentAlreadyExistsException;
import tech.goticket.backendapi.shared.model.status.Status;
import tech.goticket.backendapi.shared.model.status.StatusRepository;
import tech.goticket.backendapi.shared.storage.FileStorageService;
import tech.goticket.backendapi.shared.storage.FileUpload;
import tech.goticket.backendapi.shared.utils.DocumentValidator;
import tech.goticket.backendapi.venue.dto.CreateVenueDTO;
import tech.goticket.backendapi.venue.dto.UpsertVenueSectorsPayloadDTO;
import tech.goticket.backendapi.venue.dto.VenueDetailDTO;
import tech.goticket.backendapi.venue.dto.VenueListDTO;
import tech.goticket.backendapi.venue.dto.VenueSectorDTO;
import tech.goticket.backendapi.shared.config.OpenApiConfig;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/venues")
@RequiredArgsConstructor
@Tag(name = "Locais", description = "Locais (venues) e seus setores")
public class VenueController {

    private final OrganizerService organizerService;

    private final VenueService venueService;

    private final StatusRepository statusRepository;

    private final FileStorageService fileStorageService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Lista locais (paginado)")
    public ResponseEntity<VenueListDTO> listVenues(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        var venues = venueService.findAll(
                PageRequest.of(page, pageSize, Sort.Direction.ASC, "name"));

        return ResponseEntity.ok(venues);
    }

    @GetMapping("/{venueId}")
    @Operation(summary = "Detalha um local pelo ID")
    public ResponseEntity<VenueDetailDTO> findVenueById(@PathVariable Long venueId) {
        Venue venue = venueService.findById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Espaço não encontrado."));

        return ResponseEntity.ok(VenueDetailDTO.fromEntity(venue));
    }

    @GetMapping(value = "/{venueId}/sector-map", produces = "image/svg+xml")
    @Operation(summary = "Retorna o mapa de setores do local (SVG)")
    public ResponseEntity<String> getVenueSectorMap(@PathVariable Long venueId) {
        Venue venue = venueService.findById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Espaço não encontrado."));

        if (venue.getSectorMapS3Key() == null || venue.getSectorMapS3Key().isBlank()) {
            throw new ResourceNotFoundException("Mapa de setores não encontrado para este espaço.");
        }

        String svgText = fileStorageService.getObjectAsText(venue.getSectorMapS3Key());
        return ResponseEntity.ok(svgText);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Cadastra um novo local")
    public ResponseEntity<VenueDetailDTO> createNewVenue(@Valid @RequestBody CreateVenueDTO dto, Authentication authentication) {
        boolean isCNPJ = DocumentValidator.isCNPJ(dto.CNPJ());
        if(!isCNPJ) { throw new InvalidArgumentException("CNPJ informado é inválido."); }

        UUID loggedUserId = UUID.fromString(authentication.getName());
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("SCOPE_ADMIN"));

        UUID targetOrganizerId;

        if(isAdmin) {
            if(dto.organizerId() == null) {
                throw new InvalidArgumentException("O ID do organizador é obrigatório quando a criação é feita por um Administrador.");
            }
            targetOrganizerId = dto.organizerId();
        }
        else {
            targetOrganizerId = loggedUserId;
        }

        Organizer organizer = organizerService.findById(targetOrganizerId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizador informado não encontrado."));

        venueService.findByCNPJ(dto.CNPJ())
                .ifPresent(venue -> { throw new DocumentAlreadyExistsException("Este CNPJ já está cadastrado no sistema para outro espaço."); });

        Instant now = Instant.now();
        Status venueStatus = statusRepository.findByName(Status.Values.INACTIVE.name());

        Venue newVenue = new Venue(
                dto.name(),
                dto.legalName(),
                dto.CNPJ(),
                dto.description(),
                dto.streetAddress(),
                dto.streetAddressNumber(),
                dto.neighborhood(),
                dto.city(),
                dto.state(),
                dto.country(),
                now,
                now,
                venueStatus,
                organizer
        );
        newVenue.setZipCode(dto.zipCode());

        venueService.saveVenue(newVenue);

        return ResponseEntity.created(URI.create("/venues/" + newVenue.getVenueId()))
                .body(VenueDetailDTO.fromEntity(newVenue));
    }

    @PatchMapping(value = "/{venueId}", consumes = "application/merge-patch+json")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Atualiza os dados do local (merge-patch)")
    public ResponseEntity<VenueDetailDTO> updateVenue(@PathVariable Long venueId,
                                                      @RequestBody JsonNode patchNode,
                                                      Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        Venue venue = venueService.updateVenue(venueId, patchNode, userId);

        return ResponseEntity.ok(VenueDetailDTO.fromEntity(venue));
    }

    @GetMapping("/{venueId}/sectors")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lista os setores do local")
    @ApiResponse(responseCode = "200", description = "Setores do local.")
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public ResponseEntity<List<VenueSectorDTO>> listVenueSectors(@PathVariable Long venueId) {
        venueService.findById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Espaço não encontrado."));

        var sectors = venueService.listVenueSectors(venueId).stream()
                .map(VenueSectorDTO::new)
                .toList();

        return ResponseEntity.ok(sectors);
    }

    @PutMapping("/{venueId}/sectors")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Substitui os setores do local")
    public ResponseEntity<List<VenueSectorDTO>> replaceVenueSectors(@PathVariable Long venueId,
                                                                    @Valid @RequestBody UpsertVenueSectorsPayloadDTO payload,
                                                                    Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        var sectors = venueService.replaceVenueSectors(venueId, payload.sectors(), userId).stream()
                .map(VenueSectorDTO::new)
                .toList();

        return ResponseEntity.ok(sectors);
    }

    @PutMapping(value = "/{venueId}/sector-map", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Envia o mapa de setores do local (SVG, multipart)")
    public ResponseEntity<VenueDetailDTO> uploadSectorMap(@PathVariable Long venueId,
                                                          @RequestParam("mapFile") MultipartFile mapFile,
                                                          Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());

        if (mapFile == null || mapFile.isEmpty()) {
            throw new InvalidArgumentException("O arquivo do mapa é obrigatório.");
        }

        String original = mapFile.getOriginalFilename() == null ? "" : mapFile.getOriginalFilename().toLowerCase();
        if (!original.endsWith(".svg")) {
            throw new InvalidArgumentException("O mapa deve ser um arquivo SVG.");
        }

        String key = "venues/" + venueId + "/maps/sector-map.svg";
        String uploadedKey = fileStorageService.uploadWithKey(new FileUpload(mapFile), key);

        Venue venue = venueService.updateVenueMapKey(venueId, uploadedKey, userId);
        return ResponseEntity.ok(VenueDetailDTO.fromEntity(venue));
    }

    @DeleteMapping("/{venueId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN', 'SCOPE_ORGANIZER')")
    @Operation(summary = "Remove um local")
    public ResponseEntity<Long> deleteVenueById(@PathVariable Long venueId,
                                                Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        venueService.deleteVenue(venueId, userId);

        return ResponseEntity.ok(venueId);
    }
}
