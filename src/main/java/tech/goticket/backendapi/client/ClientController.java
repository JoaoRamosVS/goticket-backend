package tech.goticket.backendapi.client;

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
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tech.goticket.backendapi.client.dto.ClientListDTO;
import tech.goticket.backendapi.client.dto.ClientProfileDTO;
import tech.goticket.backendapi.client.dto.CreateClientDTO;
import tech.goticket.backendapi.client.dto.UpdatePasswordDTO;
import tech.goticket.backendapi.shared.utils.DocumentValidator;
import tech.goticket.backendapi.user.Role;
import tech.goticket.backendapi.user.dto.LoginResponse;
import tech.goticket.backendapi.shared.exception.InvalidArgumentException;
import tech.goticket.backendapi.shared.exception.ResourceNotFoundException;
import tech.goticket.backendapi.shared.exception.user.DocumentAlreadyExistsException;
import tech.goticket.backendapi.shared.exception.user.EmailAlreadyExistsException;
import tech.goticket.backendapi.user.repository.RoleRepository;
import tech.goticket.backendapi.shared.model.status.Status;
import tech.goticket.backendapi.shared.model.status.StatusRepository;
import tech.goticket.backendapi.user.UserService;
import tech.goticket.backendapi.user.token.AuthTokenService;
import tech.goticket.backendapi.shared.config.ApiError;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping(value = "/clients")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Cadastro e gestão de clientes (compradores)")
public class ClientController {

    private final UserService userService;
    private final RoleRepository roleRepository;
    private final ClientService clientService;
    private final StatusRepository statusRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;

    @PostMapping
    @Operation(summary = "Cadastra um novo cliente e retorna os tokens de sessão")
    @ApiResponse(responseCode = "201", description = "Cliente criado; retorna os tokens de sessão.")
    @ApiResponse(responseCode = "400", description = "Body inválido ou CPF com dígitos verificadores inválidos.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples =
                    @ExampleObject(value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 400, "status": "BAD_REQUEST",
                             "errors": ["CPF informado é inválido."]}""")))
    @ApiResponse(responseCode = "422", description = "E-mail ou CPF já cadastrado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {
                    @ExampleObject(name = "E-mail duplicado", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 422, "status": "UNPROCESSABLE_ENTITY",
                             "errors": ["Este e-mail já está cadastrado."]}"""),
                    @ExampleObject(name = "CPF duplicado", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 422, "status": "UNPROCESSABLE_ENTITY",
                             "errors": ["Este CPF já está cadastrado."]}""")
            }))
    public ResponseEntity<LoginResponse> createNewClient(@Valid @RequestBody CreateClientDTO dto) {

        boolean isCpf = DocumentValidator.isCPF(dto.identityDocument());
        if (!isCpf) { throw new InvalidArgumentException("CPF informado é inválido."); }

        userService.findByEmail(dto.email())
                .ifPresent(user -> { throw new EmailAlreadyExistsException("Este e-mail já está cadastrado."); });

        clientService.findByIdentityDocument(dto.identityDocument())
                .ifPresent(client -> { throw new DocumentAlreadyExistsException("Este CPF já está cadastrado."); });

        Role clientRole = roleRepository.findByName(Role.Values.CLIENT.name());
        Status clientStatus = statusRepository.findByName(Status.Values.ACTIVE.name());
        Instant now = Instant.now();

        var client = new Client(
                dto.email(),
                passwordEncoder.encode(dto.password()),
                clientRole,
                clientStatus,
                dto.fullName(),
                dto.sex(),
                dto.identityDocument(),
                LocalDate.parse(dto.birthDate()),
                now,
                now
        );

        clientService.saveClient(client);

        return ResponseEntity.created(URI.create("/clients/" + client.getUserId()))
                .body(authTokenService.issueTokens(client));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    @Operation(summary = "Lista clientes (paginado) — administração")
    public ResponseEntity<ClientListDTO> listClients(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        ClientListDTO clients = clientService.findAll(
                PageRequest.of(page, pageSize, Sort.Direction.ASC, "fullName"));

        return ResponseEntity.ok(clients);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('SCOPE_CLIENT')")
    @Operation(summary = "Retorna o perfil do cliente autenticado")
    public ResponseEntity<ClientProfileDTO> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(
                clientService.getProfile(UUID.fromString(authentication.getName()))
        );
    }

    @GetMapping("/{clientId}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') || authentication.name == #clientId")
    @Operation(summary = "Detalha um cliente pelo ID")
    public ResponseEntity<Client> getClientById(@PathVariable String clientId) {
        UUID uuid = UUID.fromString(clientId);
        Client client = this.clientService.findById(uuid).
                orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));

        return ResponseEntity.ok(client);
    }

    @PatchMapping("/{clientId}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN') || authentication.name == #clientId")
    @Operation(summary = "Atualiza os dados do cliente")
    public ResponseEntity<Client> updateClient(@PathVariable String clientId,
                                               @RequestBody JsonNode patchNode,
                                               Authentication authentication) {
        UUID uuid = UUID.fromString(clientId);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SCOPE_ADMIN"));

        Client updatedClient = this.clientService.updateClient(uuid, patchNode, isAdmin);
        return ResponseEntity.ok(updatedClient);
    }

    @PatchMapping("/{clientId}/password")
    @PreAuthorize("authentication.name == #clientId")
    @Operation(summary = "Altera a senha do cliente")
    public ResponseEntity<Void> updatePassword(@PathVariable String clientId,
                                               @Valid @RequestBody UpdatePasswordDTO dto) {
        clientService.updatePassword(UUID.fromString(clientId), dto);
        return ResponseEntity.noContent().build();
    }
}
