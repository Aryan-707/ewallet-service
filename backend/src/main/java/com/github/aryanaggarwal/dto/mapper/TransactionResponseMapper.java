package com.github.aryanaggarwal.dto.mapper;

import com.github.aryanaggarwal.dto.response.TransactionResponse;
import com.github.aryanaggarwal.domain.entity.Transaction;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static com.github.aryanaggarwal.common.Constants.DATE_TIME_FORMAT;

/**
 * Mapper used for mapping TransactionResponse fields.
 */
@Mapper(componentModel = "spring")
public interface TransactionResponseMapper {

    Transaction toTransaction(TransactionResponse dto);

    @Mapping(target = "createdAt", ignore = true)
    TransactionResponse toTransactionResponse(Transaction entity);

    @AfterMapping
    default void formatCreatedAt(@MappingTarget TransactionResponse dto, Transaction entity) {
        LocalDateTime datetime = LocalDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC);
        dto.setCreatedAt(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT).format(datetime));
    }
}
