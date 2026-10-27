package com.accenture.externalapis.demo.client;

import com.accenture.externalapis.demo.config.ExternalServiceProperties;
import com.accenture.externalapis.demo.dto.BookApiResponse;
import com.accenture.externalapis.demo.dto.BookDto;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Component
public class BookRestClientImpl implements BookRestClient {

    private final RestClient restClient;

    public BookRestClientImpl(RestClient.Builder builder, ExternalServiceProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(5));

        builder.baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));

        if (properties.token() != null && !properties.token().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token());
        }
        this.restClient = builder.build();

    }

    public BookDto getBook(Long id){
        try {
            BookApiResponse response = restClient.get().
                    uri("/books/{id}", id)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,(req,res) -> {
                        throw new ClientException("Client error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError,(req,res) -> {
                        throw new ClientException("External service error: " + res.getStatusCode());
                    })
                    .body(BookApiResponse.class);
            return toDto(response);
        }
        catch (HttpClientErrorException.NotFound e) {
            throw new ClientException("Book not found: " + id, e);
        }
        catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException
                    || e.getCause() instanceof HttpTimeoutException) {
                throw new ClientException("External service timed out", e);
            }
            throw new ClientException("External service unreachable: " + e.getMessage(), e);
        }
    }

    public List<BookDto> getAllBooks(){
        try {
            BookApiResponse[] response = restClient.get()
                    .uri("/books")
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,(req,res) -> {
                        throw new ClientException("Client error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError,(req,res) -> {
                        throw new ClientException("External service error: " + res.getStatusCode());
                    })
                    .body(BookApiResponse[].class);
            if (response == null) {
                return List.of();
            }
            return Arrays.stream(response).map(this::toDto).toList();
        }
        catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException
                    || e.getCause() instanceof HttpTimeoutException) {
                throw new ClientException("External service timed out", e);
            }
            throw new ClientException("External service unreachable: " + e.getMessage(), e);
        }
    }
    private BookDto toDto(BookApiResponse response) {
        return new BookDto(response.title(), response.author(), response.genre(), response.price());
    }
}
