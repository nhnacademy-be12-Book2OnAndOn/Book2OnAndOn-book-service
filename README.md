# Book2OnAndOn - Book Service

Book2OnAndOn 프로젝트의 핵심 비즈니스 로직을 담당하는 **Book Microservice**입니다. 
도서 정보 관리, MinIO 기반의 이미지 스토리지 연동, 그리고 대규모 언어 모델(LLM)을 활용한 자동화된 데이터 보강(Data Enrichment) 및 지능형 검색 기능을 제공합니다.

## 🚀 Key Features

### 1. 지능형 도서 데이터 보강 (AI Data Enrichment)
도서의 기본 정보만으로 메타데이터를 풍부하게 만드는 자동화 파이프라인을 구축했습니다.
* **Aladin API 연동**: ISBN을 기반으로 알라딘 API와 연동하여 카테고리, 저자, 출판사, 고화질 표지 이미지를 자동으로 가져옵니다.
* **AI 태그 및 챕터 자동 생성**: 책의 제목과 설명을 바탕으로 **Groq API**와 **Gemini API**를 활용해 키워드 태그와 목차(Chapter)를 생성합니다.
* **안정적인 Fallback 및 Rate Limiting**: API 호출 할당량(Quota) 초과 시 Groq에서 Gemini로 자동 전환되는 Fallback 로직과 Rate Limiter가 적용되어 있습니다.

### 2. AI 기반 지능형 검색 및 캐싱 (Smart Search & Warmup)
단순한 텍스트 검색을 넘어 문맥을 이해하는 검색 경험을 제공합니다.
* **Vector Embedding**: **Ollama API**를 통해 검색어의 임베딩 벡터를 실시간으로 생성하여 유사도 검색(FastPath)을 수행합니다.
* **Reranking & AI Recommendation**: 검색된 1차 후보군을 **RabbitMQ** 비동기 큐를 통해 백그라운드에서 Reranking 하고, Gemini를 통해 최종 추천 결과를 생성합니다.
* **Redis Caching**: 임베딩 결과와 AI 추천 결과를 Redis에 캐싱하여(TTL 지원) 동일한 검색어에 대한 응답 속도를 극대화했습니다.

### 3. 클라우드 네이티브 이미지 관리 (Image Storage)
* **MinIO 연동**: 외부 도서 이미지 URL을 스트리밍 방식으로 다운로드하여 내부 MinIO 오브젝트 스토리지로 마이그레이션 및 업로드합니다.
* 화질 개선 로직 및 404/시스템 인터럽트 등의 엣지 케이스에 대한 정밀한 예외 처리가 구현되어 있습니다.

## 🏗 Architecture & Code Quality

본 서비스는 높은 유지보수성과 테스트 용이성을 위해 엄격한 코드 품질 기준을 따르고 있습니다.
* **Single Responsibility Principle (SRP)**: 트랜잭션이 필요한 DB 조작 레이어와 외부 API와 통신하는 I/O 레이어(e.g., `TagEnrichmentService` vs `TagGenerationService`)를 철저히 분리했습니다.
* **Low Cognitive Complexity**: 모든 핵심 비즈니스 메서드는 인지 복잡도(Cognitive Complexity) 15 이하로 유지되도록 설계 및 리팩토링 되었습니다.
* **Robust Error Handling**: 제네릭한 `Exception` 대신 `InternalImageUploadException`, `EmbeddingFetchException` 등 도메인에 특화된 Custom Exception을 사용하여 장애 추적성을 높였습니다.
* **Fast & Reliable Testing**: `Thread.sleep()` 같은 비결정적 테스트 방식을 배제하고, `CountDownLatch`와 Parameterized Tests를 활용하여 빠르고 커버리지가 높은 단위 테스트를 유지합니다.

## 🛠 Tech Stack
* **Framework**: Spring Boot 3.x, Spring Data JPA
* **Database & Cache**: MySQL (or equivalents), Redis
* **Message Broker**: RabbitMQ
* **Storage**: MinIO (S3 Compatible)
* **AI & Search**: Ollama, Groq, Gemini, Elasticsearch (Search Client)
* **Test**: JUnit 5, AssertJ, Mockito

## ⚙️ Configuration
서비스 실행 전 다음 환경 변수 또는 `application.yml` 설정이 필요합니다.
```yaml
minio:
  url: http://localhost:9000
  public-url: http://localhost:9000
  bucket-name: book2onandon
  folder:
    book: books
    review: reviews

search:
  embedding:
    timeout: 2 # 임베딩 생성 타임아웃 (초)

rabbitmq:
  exchange:
    search-warmup: warmup-exchange
  routing:
    search-warmup: warmup-key
```
