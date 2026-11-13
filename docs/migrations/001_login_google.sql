-- Migração: login exclusivo com Google (OAuth2)
--
-- Por que é manual: o "ddl-auto: update" do Hibernate só ADICIONA colunas/tabelas.
-- Ele cria sozinho as colunas novas (google_sub, name, picture_url) e o índice único
-- de google_sub, mas NÃO remove o NOT NULL da coluna "password". Como a entidade não tem
-- mais senha, todo cadastro novo falharia com "null value in column password".
--
-- Rode UMA vez no Postgres (antes ou logo depois do deploy):

ALTER TABLE usuarios ALTER COLUMN password DROP NOT NULL;

-- Opcional, quando tiver certeza de que não vai voltar ao login com senha
-- (apaga os hashes BCrypt antigos, que não são mais usados):
-- ALTER TABLE usuarios DROP COLUMN password;
