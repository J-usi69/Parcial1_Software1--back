package com.workflow.dto.request;

import com.workflow.domain.enums.RolUsuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para asignar o reasignar un revisor a una solicitud de Workflow")
public class AsignarUsuarioRequest {

    @NotBlank(message = "El usuario a asignar es obligatorio")
    @Schema(description = "Identificador del usuario que será responsable de la solicitud", example = "maria.revisora")
    private String usuarioAsignado;

    @NotBlank(message = "El usuario responsable (el que realiza la acción) es obligatorio")
    @Schema(description = "Identificador del usuario que realiza la operación actual", example = "admin.sistema")
    private String usuarioResponsable;

    @NotNull(message = "El rol del usuario que realiza la acción es obligatorio")
    @Schema(description = "Rol de quien asigna (generalmente ADMIN o Jefe de Revisor)", example = "ADMINISTRADOR")
    private RolUsuario rolUsuario;
}
