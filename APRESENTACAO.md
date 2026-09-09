# Roteiro de apresentação (5–8 minutos)

1. **Objetivo e arquitetura (1 min):** mostrar o diagrama no README. Explicar que cada cliente é um processo independente, conectado por TCP ao servidor que mantém os perfis.
2. **Dados e algoritmo (1–2 min):** executar `ARTISTAS`, `USUARIOS` e `PERFIL Ana`. Mostrar o significado de zero e calcular √2 usando o segundo exemplo do enunciado. Explicar os três vizinhos e a média ponderada.
3. **Recomendação (1 min):** executar `RECOMENDAR Ana`. Mostrar os vizinhos Helena e Joao com distância zero nas avaliações conhecidas em comum. Explicar que perfis podem ter distância zero e ainda diferir nas posições ignoradas. Observar os nomes e notas estimadas dos candidatos.
4. **Concorrência e estado compartilhado (2 min):** deixar um cliente aberto e ocioso. Em um segundo cliente, executar `CADASTRAR Lucas` e `AVALIAR Lucas 1 4`. No primeiro, executar `PERFIL Lucas` e mostrar que a atualização foi recebida pelo servidor e está disponível às outras conexões. Executar `AVALIAR Lucas 2 3`, `AVALIAR Lucas 3 4` e `RECOMENDAR Lucas`.
5. **Validação e atualização (1 min):** executar `AVALIAR Lucas 1 9`, mostrar o erro e depois `ARTISTAS` para comprovar que a conexão continua funcional. Executar `AVALIAR Ana 5 4` e `RECOMENDAR Ana`: Nirvana deixa de ser candidato, pois já foi avaliado.
6. **Testes e limites (1 min):** executar `mvn test` e mostrar os resultados. Explicar o teste com 12 clientes simultâneos, a sincronização, o armazenamento em memória e a ausência de autenticação. Encerrar um cliente com `SAIR` e mostrar que o outro continua funcionando.

Antes da demonstração, reinicie o servidor para restaurar os dez perfis iniciais. Para demonstrar comunicação entre máquinas, use o IP do servidor no comando do cliente. A execução local em vários terminais também demonstra processos distintos comunicando-se por sockets TCP.

## Demonstração pelo navegador

Inicie o servidor e abra http://localhost:8080. Selecione Ana para visualizar as recomendações iniciais. Cadastre outro usuário e salve algumas notas. Em um cliente TCP, execute `PERFIL nome` para mostrar que os dados cadastrados na página são os mesmos mantidos pelo servidor central.
