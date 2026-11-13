vivaleve.proj
├── DemoApplication.java
│
├── config/
│   ├── SecurityConfig.java          (CORS + JWT resource server HS256; /v1/auth/** e /error públicos)
│   └── CurrentUser.java             (extrai o id do usuário do claim "sub" do JWT)
│
├── controller/                      ← C: recebe a requisição e devolve a resposta
│   ├── AuthController.java          (POST /v1/auth/google — troca o ID Token do Google pelo JWT da API)
│   ├── UserController.java          (GET /v1/users/me)
│   ├── EntryController.java         (CRUD /v1/entries — sempre do usuário do token)
│   └── StatsController.java         (GET /v1/stats?days=7|30)
│
├── service/                         ← regras de negócio
│   ├── AuthService.java             (login/cadastro via Google, vínculo de conta antiga, emissão do JWT)
│   ├── GoogleTokenVerifier.java     (valida assinatura, iss, aud, exp e email_verified do ID Token)
│   ├── UserService.java             (perfil + conversão UserEntity → UserResponseDto)
│   ├── EntryService.java            (CRUD com checagem de dono → 404 se for de outro usuário)
│   └── StatsService.java            (evolução, distribuição, predominante, streak)
│
├── repository/                      ← acesso a dados (Spring Data JPA)
│   ├── UserRepository.java          (findByGoogleSub, findByEmail)
│   └── EntryRepository.java         (findByIdAndUser_Id, por período, datas distintas)
│
├── model/                           ← M: entidades e enums
│   ├── BaseEntity.java              (id, createdAt, updatedAt)
│   ├── UserEntity.java              (tabela usuarios: email, googleSub, name, pictureUrl — sem senha)
│   ├── EntryEntity.java             (tabela registros: mood, activities, note, date, user)
│   └── enums/
│       ├── Mood.java                (GREAT=5 … AWFUL=1)
│       └── Activity.java
│
├── dto/                             ← V: formato do JSON de entrada e saída
│   ├── AuthDtos.java                (records: GoogleLoginRequest, AuthResponse)
│   ├── UserResponseDto.java         (id, email, name, pictureUrl)
│   ├── EntryDtos.java               (EntryRequest, EntryResponse)
│   └── StatsDtos.java               (StatsSummary, MoodEvolutionPoint, MoodDistributionItem)
│
└── exceptions/                      (ainda não usadas — os services lançam ResponseStatusException)
    ├── BadRequestExceptions.java
    ├── NotFoundExceptions.java
    └── ErrorResponse.java

src/main/resources
└── application.yml                  (datasource, jwt, google.client-id, cors — tudo via variáveis de ambiente)

src/test/java/vivaleve/proj
├── DemoApplicationTests.java        (contextLoads — precisa de banco e variáveis de ambiente)
├── AuthServiceGoogleTest.java
├── GoogleTokenVerifierTest.java
├── EntryServiceOwnershipTest.java
├── SecurityRulesTest.java
└── config/SecurityConfigTestSimple.java

docs/
├── estrutura.md
└── migrations/001_login_google.sql  (rodar uma vez: remove o NOT NULL de usuarios.password)
