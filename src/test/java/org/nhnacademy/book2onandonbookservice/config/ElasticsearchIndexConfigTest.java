package org.nhnacademy.book2onandonbookservice.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.exception.ElasticsearchInitializationException;

import java.io.IOException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElasticsearchIndexConfigTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @InjectMocks
    private ElasticsearchIndexConfig elasticsearchIndexConfig;

    @Test
    @DisplayName("성공: 인덱스가 이미 존재하는 경우 생성 로직 건너뜀")
    void initIndex_IndexExists() throws IOException {
        when(elasticsearchClient.indices()).thenReturn(indicesClient);

        BooleanResponse mockBooleanResponse = mock(BooleanResponse.class);
        when(mockBooleanResponse.value()).thenReturn(true);
        when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(mockBooleanResponse);

        elasticsearchIndexConfig.initIndex();

        verify(indicesClient, times(1)).exists(any(ExistsRequest.class));
        verify(indicesClient, never()).create(any(Function.class));
    }

    @Test
    @DisplayName("성공: 인덱스가 없는 경우 파일 읽어서 생성 (파일 존재 가정)")
    void initIndex_IndexNotExists_Success() throws IOException {
        when(elasticsearchClient.indices()).thenReturn(indicesClient);

        BooleanResponse mockBooleanResponse = mock(BooleanResponse.class);
        when(mockBooleanResponse.value()).thenReturn(false);
        when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(mockBooleanResponse);

        CreateIndexResponse mockCreateResponse = mock(CreateIndexResponse.class);
        when(indicesClient.create(any(Function.class))).thenReturn(mockCreateResponse);

        assertThatCode(() -> elasticsearchIndexConfig.initIndex())
                .doesNotThrowAnyException();

        verify(indicesClient, times(1)).exists(any(ExistsRequest.class));
        verify(indicesClient, times(1)).create(any(Function.class));
    }

    @Test
    @DisplayName("실패: Elasticsearch 통신 중 IOException 발생 시 예외 던짐")
    void initIndex_IOException() throws IOException {
        when(elasticsearchClient.indices()).thenReturn(indicesClient);

        when(indicesClient.exists(any(ExistsRequest.class))).thenThrow(new IOException("Connection error"));

        assertThatThrownBy(() -> elasticsearchIndexConfig.initIndex())
                .isInstanceOf(ElasticsearchInitializationException.class)
                .hasMessage("Failed to initialize Elasticsearch index");
    }
}