package com.accenture.externalapis.demo.client;

import com.accenture.externalapis.demo.config.ExternalServiceProperties;
import com.accenture.externalapis.demo.dto.BookApiResponse;
import com.accenture.externalapis.demo.dto.BookDto;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class BookWebClientImpl implements BookWebClient {

    private final WebClient webClient;

    public BookWebClientImpl(WebClient.Builder builder, ExternalServiceProperties properties) {
        builder.baseUrl(properties.baseUrl());
        if (properties.token() != null && !properties.token().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.token());
        }
        this.webClient = builder.build();
    }

    public Mono<BookDto> getBookAsync(Long id) {
        return webClient.get()
                .uri("/books/{id}", id)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        r -> Mono.error(new ClientException("Client error: " + r.statusCode())))
                .onStatus(HttpStatusCode::is5xxServerError,
                        r -> Mono.error(new ClientException("External service error: " + r.statusCode())))
                .bodyToMono(BookApiResponse.class)
                .map(this::toDto)
                .onErrorMap(WebClientRequestException.class,
                        e -> new ClientException("External service unreachable: " + e.getMessage(), e));
    }

    public Flux<BookDto> getAllBooksAsync() {
        return webClient.get()
                .uri("/books")
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        r -> Mono.error(new ClientException("Client error: " + r.statusCode())))
                .onStatus(HttpStatusCode::is5xxServerError,
                        r -> Mono.error(new ClientException("External service error: " + r.statusCode())))
                .bodyToFlux(BookApiResponse.class)
                .map(this::toDto)
                .onErrorMap(WebClientRequestException.class,
                        e -> new ClientException("External service unreachable: " + e.getMessage(), e));
    }
    public Mono<List<BookDto>> getBooksInParallel(Long id1, Long id2) {
        return Mono.zip(getBookAsync(id1), getBookAsync(id2))
                .map(t -> List.of(t.getT1(), t.getT2()));
    }
    private BookDto toDto(BookApiResponse response) {
        return new BookDto(response.title(), response.author(), response.genre(), response.price());
    }
}
