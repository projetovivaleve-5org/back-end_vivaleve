br.com.pluma
├── PlumaApplication.java
│
├── config/
│   └── SecurityConfig.java          (CORS + JWT resource server)
│
├── controller/                      ← C: recebe a requisição e devolve a resposta
│   ├── AuthController.java          (POST /auth/register, /auth/login)
│   ├── UserController.java          (GET/PUT /users/me)
│   ├── EntryController.java         (CRUD /entries)
│   └── StatsController.java         (GET /stats?days=7|30)
│
├── service/                         ← regras de negócio
│   ├── AuthService.java
│   ├── UserService.java
│   ├── EntryService.java
│   └── StatsService.java            (evolução, distribuição, predominante, streak)
│
├── repository/                      ← acesso a dados (Spring Data JPA)
│   ├── UserRepository.java
│   └── EntryRepository.java
│
├── model/                           ← M: entidades e enums
│   ├── BaseEntity.java              (id, createdAt, updatedAt)
│   ├── User.java
│   ├── Entry.java                   (mood, activities, note, date, user)
│   └── enums/
│       ├── Mood.java
│       └── Activity.java
│
├── dto/                             ← V: formato do JSON de entrada e saída
│   ├── AuthDtos.java                (records: RegisterRequest, LoginRequest, AuthResponse)
│   ├── UserDtos.java                (UserResponse, UpdateProfileRequest)
│   ├── EntryDtos.java               (EntryRequest, EntryResponse)
│   └── StatsDtos.java               (StatsSummary, MoodEvolutionPoint, MoodDistributionItem)
│
└── exception/
├── GlobalExceptionHandler.java  (@RestControllerAdvice)
├── BusinessException.java
└── NotFoundException.java