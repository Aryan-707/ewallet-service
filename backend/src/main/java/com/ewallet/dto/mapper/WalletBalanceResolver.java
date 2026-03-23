package com.ewallet.dto.mapper;

import com.ewallet.domain.entity.Wallet;
import com.ewallet.dto.response.WalletResponse;
import com.ewallet.service.BalanceService;
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
