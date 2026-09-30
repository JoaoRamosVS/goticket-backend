package tech.goticket.backendapi.organizer;


import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tech.goticket.backendapi.organizer.dto.CreateOrganizerDTO;
import tech.goticket.backendapi.organizer.dto.OrganizerListDTO;
import tech.goticket.backendapi.user.dto.LoginResponse;
import tech.goticket.backendapi.user.Role;
import tech.goticket.backendapi.shared.model.status.Status;
import tech.goticket.backendapi.shared.exception.InvalidArgumentException;
import tech.goticket.backendapi.shared.exception.ResourceNotFoundException;
import tech.goticket.backendapi.shared.exception.user.DocumentAlreadyExistsException;
import tech.goticket.backendapi.shared.exception.user.EmailAlreadyExistsException;
import tech.goticket.backendapi.user.repository.RoleRepository;
import tech.goticket.backendapi.shared.model.status.StatusRepository;
import tech.goticket.backendapi.user.UserService;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import tech.goticket.backendapi.shared.utils.DocumentValidator;
import tech.goticket.backendapi.user.token.AuthTokenService;
import tech.goticket.backendapi.shared.config.ApiError;

@RestController
@RequestMapping(value = "/organizers")
@RequiredArgsConstructor
@Tag(name = "Organizadores", description = "Cadastro e gestão de organizadores de eventos")
public class OrganizerController {

    private final UserService userService;

    private final RoleRepository roleRepository;

    private final OrganizerService organizerService;

    private final StatusRepository statusRepository;

    private final BCryptPasswordEncoder passwordEncoder;

    private final AuthTokenService authTokenService;

    @PostMapping
    @Operation(summary = "Cadastra um novo organizador e retorna os tokens de sessão")
    @ApiResponse(responseCode = "201", description = "Organizador criado; retorna os tokens de sessão.")
    @ApiResponse(responseCode = "400", description = "Body inválido ou CNPJ com dígitos verificadores inválidos.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples =
                    @ExampleObject(value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 400, "status": "BAD_REQUEST",
                             "errors": ["CNPJ informado é inválido."]}""")))
    @ApiResponse(responseCode = "422", description = "E-mail ou CNPJ já cadastrado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {
                    @ExampleObject(name = "E-mail duplicado", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 422, "status": "UNPROCESSABLE_ENTITY",
                             "errors": ["Este e-mail já está cadastrado."]}"""),
                    @ExampleObject(name = "CNPJ duplicado", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 422, "status": "UNPROCESSABLE_ENTITY",
                             "errors": ["Este CNPJ já está cadastrado."]}""")
            }))
    public ResponseEntity<LoginResponse> createNewOrganizer(@Valid @RequestBody CreateOrganizerDTO dto) {
        boolean isCNPJ = DocumentValidator.isCNPJ(dto.CNPJ());
        if (!isCNPJ) { throw new InvalidArgumentException("CNPJ informado é inválido."); }

        userService.findByEmail(dto.email())
                .ifPresent(user -> { throw new EmailAlreadyExistsException("Este e-mail já está cadastrado."); });

        organizerService.findByCNPJ(dto.CNPJ())
                .ifPresent(organizer -> { throw new DocumentAlreadyExistsException("Este CNPJ já está cadastrado."); });

        Role orgazinerRole = roleRepository.findByName(Role.Values.ORGANIZER.name());
        Status organizerStatus = statusRepository.findByName(Status.Values.ACTIVE.name());
        Instant now = Instant.now();

        Organizer organizer = new Organizer();
        organizer.setEmail(dto.email());
        organizer.setPassword(passwordEncoder.encode(dto.password()));
        organizer.setRole(orgazinerRole);
        organizer.setStatus(organizerStatus);
        organizer.setOrganizerName(dto.organizerName());
        organizer.setLegalName(dto.legalName());
        organizer.setCNPJ(dto.CNPJ());
        organizer.setRegisterDate(now);
        organizer.setLastUpdateDate(now);

        organizerService.saveOrganizer(organizer);

        return ResponseEntity.created(URI.create("/organizers/" + organizer.getUserId()))
                .body(authTokenService.issueTokens(organizer));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    @Operation(summary = "Lista organizadores (paginado) — administração")
    public ResponseEntity<OrganizerListDTO> listOrganizers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        OrganizerListDTO organizers = organizerService.findAll(
                PageRequest.of(page, pageSize, Sort.Direction.ASC, "organizerName"));

        return ResponseEntity.ok(organizers);
    }

    @GetMapping("/{organizerId}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') || authentication.name == #organizerId")
    @Operation(summary = "Detalha um organizador pelo ID")
    public ResponseEntity<Organizer> getOrganizerById(@PathVariable String organizerId) {
        UUID uuid = UUID.fromString(organizerId);
        Organizer organizer = this.organizerService.findById(uuid).
                orElseThrow(() -> new ResourceNotFoundException("Usuário organizador não encontrado."));

        return ResponseEntity.ok(organizer);
    }

    @PatchMapping("/{organizerId}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') || authentication.name == #organizerId")
    @Operation(summary = "Atualiza os dados do organizador")
    public ResponseEntity<Organizer> updateOrganizer(@PathVariable String organizerId,
                                                     @RequestBody JsonNode patchNode) {
        UUID uuid = UUID.fromString(organizerId);
        Organizer updatedOrganizer = this.organizerService.updateOrganizer(uuid, patchNode);

        return ResponseEntity.ok(updatedOrganizer);
    }
}
