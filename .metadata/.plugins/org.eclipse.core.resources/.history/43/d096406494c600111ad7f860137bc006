-- Nascimento e CPF do servidor: sao a chave que o cadastro publico do CNES pede para
-- devolver o CNS (consulta-cns). Sem esses dois no cadastro, o CNS do profissional
-- continuava sendo digitado a mao - origem classica de rejeicao no faturamento, que so
-- aparece semanas depois com a competencia ja fechada.
--
-- Guardados como texto no formato que a tela mostra (nascimento dd/MM/yyyy, CPF com
-- pontuacao), igual ao cadastro de pacientes, para nao criar um segundo formato de data
-- dentro do mesmo sistema.
--
-- Sem NOT NULL de proposito: a obrigatoriedade e do formulario novo; os servidores ja
-- cadastrados ficam com a coluna vazia ate serem editados.
ALTER TABLE servidor
    ADD COLUMN IF NOT EXISTS nascimento VARCHAR(10);

ALTER TABLE servidor
    ADD COLUMN IF NOT EXISTS cpf VARCHAR(16);
