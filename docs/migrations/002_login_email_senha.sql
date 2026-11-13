-- Migração: volta o login com email + senha (junto com o login Google)
--
-- A coluna "password" continua existindo no banco (a 001 só tirou o NOT NULL), então o
-- "ddl-auto: update" não precisa criar nada. Se alguém rodou o DROP COLUMN opcional da 001,
-- o Hibernate cria a coluna de novo sozinho no próximo deploy.
--
-- O que precisa ser feito à mão: na primeira versão da API (antes do login Google), o
-- cadastro salvava a senha SEM criptografia. Essas senhas não funcionam com o BCrypt e
-- ficariam expostas no banco. Este comando apaga toda senha que não é um hash BCrypt
-- (hashes BCrypt sempre começam com "$2").
--
-- Quem perder a senha aqui pode entrar com o Google do mesmo email (a conta é mantida).
--
-- Rode UMA vez no Postgres (antes ou logo depois do deploy):

UPDATE usuarios SET password = NULL WHERE password IS NOT NULL AND password NOT LIKE '$2%';
