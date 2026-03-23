package com.ewallet.dto.mapper;

import com.ewallet.dto.response.UserResponse;
import com.ewallet.dto.response.WalletResponse;
import com.ewallet.domain.entity.User;
import com.ewallet.domain.entity.Wallet;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.text.MessageFormat;

/**
 * Mapper used for mapping WalletResponse fields.
 */
@Mapper(componentModel = "spring", uses = WalletBalanceResolver.class)
public interface WalletResponseMapper {

    Wallet toWallet(WalletResponse dto);

    @org.mapstruct.Mapping(target = "balance", ignore = true)
    WalletResponse toWalletResponse(Wallet entity);

    @AfterMapping
    default void setFullName(@MappingTarget UserResponse dto, User entity) {
        dto.setFullName(MessageFormat.format("{0} {1}", entity.getFirstName(), entity.getLastName()));
    }
}
