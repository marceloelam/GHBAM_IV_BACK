# Sistema de Limpeza Automática de Usuários Inativos

## 📋 Visão Geral

Este sistema implementa a limpeza automática de usuários que não enviam heartbeat há mais de 3,5 minutos, executando a verificação a cada 3 minutos.

## 🔧 Componentes Implementados

### 1. **UsuariosOnlineCleanupJob** (`br.com.iv.scheduler.UsuariosOnlineCleanupJob`)

Job agendado que executa a limpeza automática.

**Características:**
- ✅ Executa a cada **3 minutos** (`@Scheduled(every = "180s")`)
- ✅ Remove usuários inativos há mais de **3 minutos e 30 segundos**
- ✅ Registra logs de todas as operações
- ✅ Exibe mensagem no console quando remove usuários

**Comportamento:**
```java
@Scheduled(every = "180s")
void cleanupInactiveUsers() {
    long cutoffTime = System.currentTimeMillis() - (3 * 60 * 1000 + 30 * 1000);
    int removed = UsuariosConectadosUtil.removeInactiveUsers(cutoffTime);
    if (removed > 0) {
        System.out.println("✓ Removidos " + removed + " usuários inativos");
    }
}
```

### 2. **UsuariosConectadosUtil.removeInactiveUsers()**

Método utilitário que realiza a limpeza efetiva.

**Características:**
- ✅ Remove usuários com `ultimaAtividade` anterior ao cutoff especificado
- ✅ Itera sobre todas as unidades
- ✅ Remove unidades vazias após limpeza
- ✅ Retorna o número total de usuários removidos
- ✅ Thread-safe (usa ConcurrentHashMap)

**Assinatura:**
```java
public static int removeInactiveUsers(long cutoffTimeMillis)
```

### 3. **Sistema de Heartbeat** (já existente)

O endpoint `/api/usuarios-conectados/heartbeat` atualiza automaticamente o timestamp do usuário:

```java
@POST
@Path("/heartbeat")
public Response heartbeat(HeartbeatRequest request) {
    UsuariosConectadosUtil.adicionarUsuario(
        request.unidade, 
        request.nome, 
        request.matricula, 
        request.cargo, 
        request.especialidade, 
        request.foto
    );
    return Response.ok().entity("{\"status\":\"ok\"}").build();
}
```

## ⏱️ Linha do Tempo de Atividade

```
Tempo          Ação
─────────────────────────────────────────────────────
0s             Usuário envia heartbeat
30s            Frontend envia novo heartbeat
60s            Frontend envia novo heartbeat
90s            Frontend envia novo heartbeat
120s           Frontend envia novo heartbeat
150s           Frontend envia novo heartbeat
180s           Job executa (1ª verificação)
180s           ✓ Usuário OK (última atividade: 150s atrás)
───────────────────────────────────────────────────── 
210s           Heartbeat para (usuário fecha navegador)
360s           Job executa (2ª verificação)
360s           ✗ Usuário REMOVIDO (última atividade: 210s atrás > 210s)
```

## 🧪 Testes

Um teste automatizado foi adicionado em `UsuariosConectadosUtilTest`:

```java
@Test
public void testRemoveInactiveUsers() throws InterruptedException {
    // Adiciona usuário ativo e inativo
    // Aguarda 2 segundos
    // Atualiza apenas o usuário ativo
    // Remove inativos com cutoff de 1 segundo
    // Verifica que apenas 1 foi removido
}
```

## 🚀 Como Funciona

### Fluxo Completo:

1. **Frontend envia heartbeat a cada 30s**
   ```javascript
   setInterval(() => {
       fetch('/api/usuarios-conectados/heartbeat', {
           method: 'POST',
           body: JSON.stringify({ unidade, matricula, nome, ... })
       });
   }, 30000);
   ```

2. **Backend atualiza timestamp**
   - `UsuariosConectadosUtil.adicionarUsuario()` chama `usuario.atualizarAtividade()`
   - Atualiza `ultimaAtividade = System.currentTimeMillis()`

3. **Job executa a cada 3 minutos**
   - Calcula cutoff: `agora - 3min30s`
   - Chama `removeInactiveUsers(cutoff)`
   - Remove usuários com `ultimaAtividade < cutoff`

4. **Usuário é removido se:**
   - Não enviou heartbeat há mais de 3,5 minutos
   - Fechou o navegador sem fazer logoff
   - Perdeu conexão com a internet
   - Aplicação travou/crashou

## 📊 Logs

O sistema gera logs detalhados:

```
FINE: Iniciando limpeza de usuários inativos...
INFO: Removendo usuário inativo: matricula=12345, nome=João Silva, unidade=uti, ultimaAtividade=1736732100000
INFO: Removidos 3 usuários inativos
FINE: Unidade pediatria removida (ficou vazia após limpeza)
```

## ⚙️ Configuração

Para ajustar os tempos, edite as constantes em `UsuariosOnlineCleanupJob`:

```java
// Intervalo de execução do job
@Scheduled(every = "180s")  // Padrão: 180s (3 minutos)

// Threshold de inatividade
private static final long INACTIVITY_THRESHOLD_MS = 3 * 60 * 1000 + 30 * 1000; // 210s (3min30s)
```

## ✅ Benefícios

1. **Memória otimizada**: Remove usuários que não estão mais ativos
2. **Lista precisa**: Exibe apenas usuários realmente online
3. **Automático**: Não depende de logoff manual
4. **Resiliente**: Funciona mesmo se o usuário fechar o navegador abruptamente
5. **Configurável**: Fácil ajustar tempos de verificação e inatividade

## 🔍 Troubleshooting

### Usuários não sendo removidos?
- Verifique se o Quarkus Scheduler está habilitado
- Confirme que a dependência `quarkus-scheduler` está no pom.xml
- Verifique os logs: `FINE: Iniciando limpeza de usuários inativos...`

### Usuários removidos muito rápido?
- Aumente `INACTIVITY_THRESHOLD_MS`
- Reduza intervalo de heartbeat no frontend

### Job não executando?
- Verifique se a classe tem `@ApplicationScoped`
- Confirme que o método tem `@Scheduled(every = "180s")`
- Reinicie o Quarkus em modo dev: `./mvnw quarkus:dev`
