package com.github.aryanaggarwal.dto.mapper;

import com.github.aryanaggarwal.dto.response.UserResponse;
import com.github.aryanaggarwal.dto.response.WalletResponse;
import com.github.aryanaggarwal.domain.entity.User;
import com.github.aryanaggarwal.domain.entity.Wallet;
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
