package iuh.fit.cartservice.client;

import iuh.fit.cartservice.dto.CustomerResponse;
import iuh.fit.shared.api.ApiResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/api/v1/user/customers/account/{accountId}")
    ApiResponse<CustomerResponse> getCustomerByAccountId(@PathVariable("accountId") String accountId);

}
