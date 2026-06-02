package iuh.fit.cartservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerResponse(
        UUID id,
        String accountId
) {
}
