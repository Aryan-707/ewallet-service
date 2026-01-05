package com.github.aryanaggarwal.dto.mapper;

import com.github.aryanaggarwal.domain.entity.Wallet;
import com.github.aryanaggarwal.dto.response.WalletResponse;
import com.github.aryanaggarwal.service.BalanceService;
import org.mapstruct.AfterMapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WalletBalanceResolver {
    @Autowired
    private BalanceService balanceService;

    @AfterMapping
    public void setBalance(@MappingTarget WalletResponse dto, Wallet entity) {
        dto.setBalance(balanceService.getBalance(entity.getId()));
    }
}
