package tech.goticket.backendapi.user;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tech.goticket.backendapi.user.dto.*;
import tech.goticket.backendapi.user.token.AuthTokenService;
import tech.goticket.backendapi.shared.config.ApiError;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Autenticação e usuários", description = "Login, refresh/logout de sessão e consulta de usuários")
public class UserController {

    private final UserService userService;

    private final AuthTokenService authTokenService;

    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @PostMapping("/login")
    @Operation(summary = "Autentica o usuário e retorna access + refresh token")
    @ApiResponse(responseCode = "200", description = "Autenticado; retorna access e refresh token.")
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas ou usuário inativo.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {
                    @ExampleObject(name = "Credenciais inválidas", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 401, "status": "UNAUTHORIZED",
                             "errors": ["E-mail ou Senha inválidos!"]}"""),
                    @ExampleObject(name = "Usuário inativo", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 401, "status": "UNAUTHORIZED",
                             "errors": ["Acesso negado, por favor entrar em contato com o suporte da plataforma."]}""")
            }))
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {

        var user = userService.findByEmail(loginRequest.email());

        user.ifPresent(User::validateUserStatus);

        if(user.isEmpty() || !user.get().isLoginCorrect(loginRequest, bCryptPasswordEncoder)) {
            throw new BadCredentialsException("E-mail ou Senha inválidos!");
        }

        return ResponseEntity.ok(authTokenService.issueTokens(user.get()));
    }

    @PostMapping("/auth/refresh")
    @Operation(summary = "Renova o access token a partir do refresh token")
    @ApiResponse(responseCode = "200", description = "Tokens renovados (o refresh token anterior é rotacionado).")
    @ApiResponse(responseCode = "401", description = "Refresh token inexistente ou expirado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {
                    @ExampleObject(name = "Não encontrado", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 401, "status": "UNAUTHORIZED",
                             "errors": ["Refresh token não encontrado."]}"""),
                    @ExampleObject(name = "Expirado", value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 401, "status": "UNAUTHORIZED",
                             "errors": ["Refresh token expirado."]}""")
            }))
    @ApiResponse(responseCode = "403", description = "Reuso de refresh token já rotacionado: toda a família de tokens da sessão é revogada.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples =
                    @ExampleObject(value = """
                            {"timestamp": "24-09-2026 14:30:00", "code": 403, "status": "FORBIDDEN",
                             "errors": ["Reuso de refresh token detectado! Toda a sessão foi invalidada por segurança."]}""")))
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authTokenService.refreshTokens(request.refreshToken()));
    }

    @PostMapping("/auth/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Encerra a sessão e revoga os refresh tokens do usuário")
    public ResponseEntity<Void> logout(Authentication authentication) {
        authTokenService.revokeAll(UUID.fromString(authentication.getName()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Retorna o usuário autenticado")
    public ResponseEntity<UserDTO> getLoggedUser(Authentication authentication){

        UUID userID = UUID.fromString(authentication.getName());

        User user = userService.findById(userID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return ResponseEntity.ok(new UserDTO(
                user.getUserId(),
                user.getEmail(),
                user.getRole(),
                user.getStatus()
        ));
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    @Operation(summary = "Lista os usuários ativos (paginado) — administração")
    public ResponseEntity<UserListDTO> listActiveUsers(@RequestParam(name = "page",defaultValue = "0") int page,
                                                       @RequestParam(name = "page",defaultValue = "10") int pageSize){
        var users = userService.findActiveUsers(PageRequest.of(page,pageSize, Sort.Direction.ASC, "email"));

        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/all")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    @Operation(summary = "Lista todos os usuários (paginado) — administração")
    public ResponseEntity<UserListDTO> listAllUsers(@RequestParam(name = "page",defaultValue = "0") int page,
                                                    @RequestParam(name = "page",defaultValue = "10") int pageSize){
        var users = userService.findAll(PageRequest.of(page,pageSize, Sort.Direction.ASC, "email"));

        return ResponseEntity.ok(users);
    }
}
