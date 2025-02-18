package com.national.pension.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class WebClientUtil {
  
  private static final Logger Logger = LoggerFactory.getLogger(WebClientUtil.class);
  private final WebClientConfig webClientConfig;
  
  public <T> T get(String url, Class<T> responseDtoClass) {
    Logger.info("##### WebClient GET CALL #####");
    return webClientConfig.webClient().method(HttpMethod.GET)
        .uri(url)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()     // retrieve() : Body 값만 가져오기, exchange() : ClientResponse 상태값과 헤더 가져오기
        .onStatus(HttpStatus::is4xxClientError, clientResponse -> Mono.error(new Exception(String.valueOf(HttpStatus.BAD_REQUEST))))
        .onStatus(HttpStatus::is5xxServerError, clientResponse -> Mono.error(new Exception(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR))))
        .bodyToMono(responseDtoClass)
        .block();
  }
  
  public <T, V> T post(String url, V request, Class<T> responseDtoClass) {
    Logger.info("##### WebClient POST CALL #####");
    return webClientConfig.webClient().method(HttpMethod.POST)
        .uri(url)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .onStatus(HttpStatus::is4xxClientError, clientResponse -> Mono.error(new Exception(String.valueOf(HttpStatus.BAD_REQUEST))))
        .onStatus(HttpStatus::is5xxServerError, clientResponse -> Mono.error(new Exception(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR))))
        .bodyToMono(responseDtoClass)
        .block();
  }
  
  public <T, V> T delete(String url, V request, Class<T> responseDtoClass) {
    Logger.info("##### WebClient DELETE CALL #####");
    return webClientConfig.webClient().method(HttpMethod.DELETE)
        .uri(url)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .onStatus(HttpStatus::is4xxClientError, clientResponse -> Mono.error(new Exception(String.valueOf(HttpStatus.BAD_REQUEST))))
        .onStatus(HttpStatus::is5xxServerError, clientResponse -> Mono.error(new Exception(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR))))
        .bodyToMono(responseDtoClass)
        .block();
  }
  
}
