# Distributed Code Search Engine (DCSE)

A distributed, fault-tolerant code search engine built using **Redis Streams**, **Apache Lucene**, and **Spring Boot**.

This system crawls source code repositories, indexes them exactly-once, and provides low-latency, relevance-ranked search with explainability.

---

## 🚀 Features

- Distributed ingestion using Redis Streams
- Consumer Groups with crash recovery (PEL)
- Exactly-once semantic indexing
- Near-real-time Lucene search
- Multi-field ranking with boosts
- Snippet highlighting
- Query explainability API
- Horizontally scalable consumers

---

## 🧱 Architecture

          ┌──────────────┐
          │   Crawler    │
          │ (Python)     │
          └──────┬───────┘
                 │
                 ▼
        ┌─────────────────┐
        │ Redis Stream     │
        │ (dcse_stream)   │
        └──────┬──────────┘
               │
     ┌─────────┴──────────┐
     │ Consumer Group     │
     │ (Spring Boot)     │
     └─────────┬──────────┘
               │
               ▼
       ┌──────────────────┐
       │ Lucene Index     │
       │ (FSDirectory)   │
       └─────────┬────────┘
                 │
                 ▼
         ┌────────────────┐
         │ Search API     │
         │ (REST)        │
         └────────────────┘


---

## 🔁 Data Flow

### Ingestion
1. Crawler parses repository files
2. Emits structured documents to Redis Stream
3. Consumers read via `XREADGROUP`
4. Documents indexed using Lucene
5. Message acknowledged only after success

### Search
1. REST API receives query
2. Multi-field Lucene query built
3. Results ranked + highlighted
4. Optional explainability returned

---

## 📦 Tech Stack

| Layer | Technology |
|----|----|
| Ingestion | Redis Streams |
| Indexing | Apache Lucene |
| Backend | Spring Boot |
| Crawler | Python |
| Storage | FSDirectory |
| Messaging | Redis Consumer Groups |

---

## 🧠 Index Schema

| Field | Type | Notes |
|----|----|----|
| id | StringField | Stable document ID |
| repo | TextField | Boosted |
| path | TextField | Boosted |
| lang | StringField | Filterable |
| code | TextField | Positions + offsets |

---

## 🔎 Search API

### Search
GET /api/search?q=builder pattern

### Explain
Returns Lucene scoring explanation for a document.

---

## ♻️ Fault Tolerance

- Messages are acknowledged only after indexing
- Crashed consumers leave messages in PEL
- Pending messages are reclaimed on restart
- Indexing is idempotent via `updateDocument`

---

## 📈 Scalability

- Multiple consumers per group
- Stateless crawlers
- Near-real-time index refresh
- Ready for index sharding

---

## 🔐 Safety & Reliability

- Stable public document IDs
- No exposure of internal Lucene doc IDs
- Graceful shutdown hooks
- Background index refresher

---

## 🛣️ Roadmap

- Index versioning & reindexing
- Repo authority scoring
- AST-aware search
- Embedding-based semantic search
- Authorization filtering

---

## 🧪 Running Locally

### Prerequisites
- Java 17+
- Redis
- Python 3.10+

### Start Redis
redis-server


### Run Indexer
./mvnw spring-boot:run


### Run Crawler
python crawler.py

---

## 📌 Why This Project

This project demonstrates:
- Distributed systems design
- Exactly-once processing
- Failure recovery
- Search relevance tuning
- Production-grade APIs

---

## 🧑‍💻 Author
Built as a learning and systems-design project.