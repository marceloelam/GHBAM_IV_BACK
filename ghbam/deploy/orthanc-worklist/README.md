# Worklist do Orthanc a partir da `worklist_v`

Servidor: `SMS-00078-SV.itg.rio` (10.200.200.78), Windows Server 2019, Orthanc 1.13.0
com o plugin `OrthancWorklists.dll` (já instalado) e `psql` do PostgreSQL 15.

```
worklist_v ──psql──► GerarWorklist.ps1 (a cada 1 min) ──► C:\OrthancWorklists\ghbam_<idimagens>.wl
                                                                  ▲
                         equipamento ──C-FIND MWL──► Orthanc :4242 (plugin Worklists)
```

O aparelho carimba `AccessionNumber = idimagens` e `PatientID = pacienteid`; o GHBAM
busca a imagem no Orthanc pelo AccessionNumber e confere o PatientID antes da IA.
O exame só entra na worklist depois que o setor de RX/TC clica **Finalizar + "Sim"** (V209).

## Passo a passo rápido

**Execute só UM arquivo**, conforme o sistema do servidor:

| Servidor | Executar | Como |
|---|---|---|
| **Windows** | `Instalar-Worklist.ps1` (na pasta da Andrea: `Instalar_Worklist.ps1`) | PowerShell **como administrador**, dentro da pasta: `powershell -ExecutionPolicy Bypass -File .\Instalar-Worklist.ps1` |
| **Linux** | `instalar_worklist.sh` | `sudo bash instalar_worklist.sh` |

**Antes:**
1. Instalar o **Orthanc** e o cliente do **PostgreSQL** (`psql`) no servidor.
2. Copiar **a pasta inteira** para o servidor (o instalador precisa do gerador na mesma pasta).

**Durante**, o instalador pergunta (Enter mantém o valor atual):
- senha do usuário `orthanc_worklist` no banco;
- pasta das imagens DICOM (recomendado disco de dados separado);
- AE Title do Orthanc na rede;
- se troca a senha do `admin` do Orthanc;
- se pode reiniciar o Orthanc.

**Os outros arquivos:**

| Arquivo | Precisa rodar? |
|---|---|
| `GerarWorklist.ps1` / `gerar_worklist.py` | Não. O instalador copia para o lugar certo e agenda a cada 1 minuto. |
| `Importar-Dicom.ps1` / `importar_dicom.sh` | Só para colocar DICOM/ZIP de teste no Orthanc. |
| `orthanc.json` | Não. É só referência; o instalador altera o `orthanc.json` **do servidor** (pasta `Configuration` do Orthanc / `/etc/orthanc`). |
| `worklist_orthanc_v.sql` / `README.md` | Não. Consulta/reserva (o backend já cria a view pelas migrations V208/V209). |

**Depois, à mão (uma vez):** cadastrar os aparelhos em `DicomModalities` no `orthanc.json`, apontar a
worklist e o envio de imagens de cada aparelho para este servidor e, se mudou IP/senha do Orthanc,
atualizar a URL na tela **/unidades** do GHBAM.

## Instalação automática (detalhes)

| Sistema | Rodar | Gerador instalado |
|---|---|---|
| Windows | PowerShell **como administrador**: `powershell -ExecutionPolicy Bypass -File .\Instalar-Worklist.ps1` | `GerarWorklist.ps1` + tarefa agendada "GHBAM Worklist" (SYSTEM, 1 min) |
| Linux (Debian/Ubuntu) | `sudo bash instalar_worklist.sh` | `gerar_worklist.py` + `ghbam-worklist.timer` (usuário orthanc, 1 min) |

Antes: Orthanc instalado (com o plugin de worklist) e `psql`. O instalador pede a senha do
`orthanc_worklist`, ajusta o `orthanc.json` (pasta das imagens DICOM, AE Title, senha do admin;
Enter mantém) com backup, e confirma antes de reiniciar o Orthanc (se não subir, desfaz sozinho).

**Importar DICOM/ZIP de teste** (nunca copiar para a pasta de armazenamento do Orthanc):
`.\Importar-Dicom.ps1 -Pasta C:\DicomTeste` (Windows) ou `bash importar_dicom.sh /caminho` (Linux). Outro banco: `-DbHost` (Windows)
ou `DB_HOST=...` (Linux). Os dois geradores produzem os mesmos `.wl`, byte a byte.
Logs: `C:\ProgramData\GHBAM\worklist.log` / `/var/log/ghbam/worklist.log`.

Os passos abaixo são o que os instaladores fazem (para conferência ou instalação manual no Windows).

## Implantação

1. **View**: a `worklist_orthanc_v` é criada pelo backend (migration `V208`, só se não
   existir) ao subir. A `worklist_v` antiga não é alterada. Antes, rodar as consultas de
   validação do fim de `worklist_orthanc_v.sql` (datas fora do padrão e valores de `local`).

2. **Usuário só-leitura no banco** (DBA; em produção exige autorização):
   ```sql
   CREATE ROLE orthanc_worklist LOGIN PASSWORD '<definir>';
   GRANT CONNECT ON DATABASE hospitalit TO orthanc_worklist;
   GRANT USAGE ON SCHEMA public TO orthanc_worklist;
   GRANT SELECT ON worklist_orthanc_v TO orthanc_worklist;  -- a V208 já faz, se o usuário existir antes
   ```
   Liberar o IP 10.200.200.78 no `pg_hba.conf` só para esse usuário.

3. **Senha fora do script** em `C:\ProgramData\GHBAM\pgpass.conf`:
   `10.200.200.7:5432:hospitalit:orthanc_worklist:<senha>`
   ```powershell
   icacls C:\ProgramData\GHBAM /inheritance:r /grant:r "SYSTEM:(OI)(CI)F" "Administradores:(OI)(CI)F"
   ```

4. **Pasta e plugin**: criar `C:\OrthancWorklists` (mesma ACL) e no `orthanc.json`:
   ```json
   "Worklists": { "Enable": true, "Database": "C:\\OrthancWorklists" }
   ```
   Conferir que `OrthancWorklists.dll` está em `"Plugins"` e reiniciar o serviço Orthanc.

5. **Testar sem gravar**: `.\GerarWorklist.ps1 -Simular` → log em `C:\ProgramData\GHBAM\worklist.log`.

6. **Agendar** (SYSTEM, a cada 1 minuto):
   ```powershell
   $a = New-ScheduledTaskAction -Execute powershell.exe -Argument '-NoProfile -ExecutionPolicy Bypass -File "C:\Program Files\Orthanc Server\GerarWorklist.ps1"'
   $t = New-ScheduledTaskTrigger -Once -At (Get-Date) -RepetitionInterval (New-TimeSpan -Minutes 1)
   Register-ScheduledTask -TaskName 'GHBAM Worklist' -Action $a -Trigger $t -User SYSTEM -RunLevel Highest
   ```

7. **No equipamento** (TI/biomédica): servidor de worklist = AE do Orthanc, 10.200.200.78:4242;
   destino de envio de imagens = o mesmo. Técnico sempre escolhe o paciente da lista.

## Cuidados

- Os `.wl` contêm nome/nascimento do paciente (necessário à MWL): pasta com ACL restrita;
  arquivos somem quando o exame sai da view (a view só lista exames do dia).
- O log registra só contagens, nunca dados do paciente.
- Falha no `psql` não apaga nada (o script aborta antes de sincronizar).
- Só arquivos `ghbam_*.wl` são gerenciados; outros `.wl` da pasta ficam intactos.
- Texto vai sem acento e em maiúsculas (compatibilidade com aparelhos antigos), `ISO_IR 100`.
