"""
Treina o modelo de SUGESTAO DE CODIFICACAO: CID (laudo de AIH + resumo de alta + anamnese
da internacao) e procedimento SIGTAP (so laudo de AIH), a partir do que os medicos ja
preencheram, e grava os pesos em
src/main/resources/ia/codificacao-aih.json.gz, que o backend Java usa (CodificacaoIaService).

- Roda fora do backend, contra uma copia do banco (dev ou o banco do municipio).
- Credenciais SO por variavel de ambiente: PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD.
- Somente leitura. Nao imprime nem grava texto clinico: o arquivo leva apenas termos que
  aparecem em >= MIN_DF laudos (sem numeros), pesos e a descricao publica de CID/SIGTAP.
- A metrica e medida com divisao temporal (treina no passado, testa nos laudos mais
  recentes) e gravada no proprio arquivo para aparecer na tela.

Uso:
    pip install psycopg2-binary scikit-learn numpy
    python tools/ia-codificacao/treinar_codificacao.py
"""
import collections
import datetime
import gzip
import html
import json
import os
import re
import sys
import unicodedata

import numpy as np
import psycopg2
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import SGDClassifier

MIN_DF = 5            # termo precisa aparecer em pelo menos 5 laudos
MIN_EXEMPLOS = 5      # codigo precisa ter pelo menos 5 laudos para virar classe
MAX_TERMOS = 40000
PESOS_POR_CLASSE = 400
SAIDA = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "ia",
                     "codificacao-aih.json.gz")

# Mesma regra do CodificacaoIaService.normalizar (Java). Alterar os dois juntos.
def normalizar(t):
    t = unicodedata.normalize("NFKD", (t or "").lower())
    t = "".join(c for c in t if not unicodedata.combining(c))
    t = re.sub(r"[0-9]+", " ", t)
    return re.sub(r"[^a-z ]+", " ", t)


def conectar():
    conn = psycopg2.connect(host=os.environ["PGHOST"], port=os.environ.get("PGPORT", "5432"),
                            dbname=os.environ["PGDATABASE"], user=os.environ["PGUSER"],
                            password=os.environ["PGPASSWORD"], connect_timeout=15)
    conn.set_session(readonly=True)
    return conn


def cid_limpo(c):
    c = re.sub(r"[^A-Z0-9]", "", (c or "").upper())
    return c if re.fullmatch(r"[A-Z][0-9]{2,3}", c) else None


def proc_limpo(p):
    p = re.sub(r"[^0-9]", "", p or "")
    return p if len(p) == 10 else None


# Palavras comuns do texto clinico que tambem sao sobrenome: continuam no vocabulario.
PALAVRAS_COMUNS = set("""dias rosa cruz branco branca campos leite flores neves paz ramos fontes prado
porto lago rei sol luz santa santo bispo serra mata pinto vale fonte rocha monte barros cabral
reis franco nobre bastos mota guerra""".split())
NOMES = set()  # preenchido em main(): partes de nomes de pacientes e servidores


def remover_nomes(texto):
    """Tira do texto qualquer palavra que seja parte de nome de pessoa cadastrada
    (assinatura do medico, nome de acompanhante etc.), para nao ir para o arquivo."""
    return " ".join(p for p in texto.split() if p not in NOMES)


def carregar_nomes(cur, descricoes_publicas):
    cur.execute("""select nome from pacientes where nome is not null
                   union all select nome from servidor where nome is not null
                   union all select nome from servidor2 where nome is not null""")
    cont = collections.Counter()
    for (n,) in cur.fetchall():
        for p in set(normalizar(n).split()):
            if len(p) >= 3:
                cont[p] += 1
    # palavra que aparece em descricao de CID/SIGTAP e vocabulario clinico, nao nome
    clinicas = {p for d in descricoes_publicas for p in normalizar(d).split()}
    return {p for p in cont if p not in clinicas and p not in PALAVRAS_COMUNS}


def treinar(X, y):
    vec = TfidfVectorizer(ngram_range=(1, 2), min_df=MIN_DF, max_features=MAX_TERMOS,
                          sublinear_tf=True, token_pattern=r"(?u)\b[a-z][a-z]+\b")
    A = vec.fit_transform(X)
    clf = SGDClassifier(loss="log_loss", alpha=1e-5, max_iter=30, random_state=42).fit(A, y)
    podar(clf)
    return vec, clf


def podar(clf):
    """Mantem so os PESOS_POR_CLASSE maiores pesos (em modulo) de cada classe."""
    W = clf.coef_
    for i in range(W.shape[0]):
        if W.shape[1] > PESOS_POR_CLASSE:
            corte = np.partition(np.abs(W[i]), -PESOS_POR_CLASSE)[-PESOS_POR_CLASSE]
            W[i][np.abs(W[i]) < corte] = 0.0


def so_frequentes(base):
    cont = collections.Counter(b[1] for b in base)
    return [b for b in base if cont[b[1]] >= MIN_EXEMPLOS]


def avaliar(por_origem):
    """Divisao temporal por origem: treina com os 80% mais antigos de TODAS as origens e
    mede em cada origem nos 20% mais recentes. O teste inclui os codigos raros (que o
    modelo nao conhece), para o numero ser o que o medico vai ver de fato."""
    treino, testes = [], {}
    for origem, base in por_origem.items():
        base = sorted(base, key=lambda b: b[2])
        corte = int(len(base) * 0.8)
        treino += base[:corte]
        testes[origem] = base[corte:]
    treino = so_frequentes(treino)
    vec, clf = treinar([b[0] for b in treino], [b[1] for b in treino])
    cls = clf.classes_
    r = {}
    for origem, te in testes.items():
        if not te:
            continue
        P = clf.predict_proba(vec.transform([b[0] for b in te]))
        top = np.argsort(-P, axis=1)[:, :5]
        real = np.array([b[1] for b in te])
        r[origem] = {
            "top1": round(float(np.mean(cls[top[:, 0]] == real)), 3),
            "top3": round(float(np.mean([real[i] in cls[top[i, :3]] for i in range(len(real))])), 3),
            "top5": round(float(np.mean([real[i] in cls[top[i, :5]] for i in range(len(real))])), 3),
            "registros_teste": len(te),
            "teste_desde": str(te[0][2]),
        }
    return r


# Frases INVENTADAS (nenhum dado real) usadas pelo teste Java para conferir que a
# inferencia em Java da o mesmo resultado que o scikit-learn.
AMOSTRAS = [
    "gestante a termo em trabalho de parto contracoes regulares",
    "pneumonia comunitaria febre tosse produtiva dispneia",
    "fratura de femur apos queda da propria altura idoso",
    "dor toracica tipica infarto agudo do miocardio supra st",
    "celulite em membro inferior com hiperemia e calor local",
]


def exportar(vec, clf, descricoes):
    nomes = vec.get_feature_names_out()
    classes = []
    for i, codigo in enumerate(clf.classes_):
        nz = np.nonzero(clf.coef_[i])[0]
        classes.append({
            "codigo": codigo,
            "descricao": descricoes.get(codigo, ""),
            "intercepto": round(float(clf.intercept_[i]), 5),
            "pesos": {str(int(j)): round(float(clf.coef_[i][j]), 5) for j in nz},
        })
    # vocabulario COMPLETO: a normalizacao L2 do TF-IDF usa todos os termos, nao so os com peso
    P = clf.predict_proba(vec.transform([normalizar(a) for a in AMOSTRAS]))
    amostras = []
    for i, a in enumerate(AMOSTRAS):
        top = np.argsort(-P[i])[:3]
        amostras.append({"texto": a, "esperado": [[clf.classes_[j], round(float(P[i][j]), 5)] for j in top]})
    return {
        "termos": [str(t) for t in nomes],
        "idf": [round(float(v), 5) for v in vec.idf_],
        "classes": classes,
        "amostras_verificacao": amostras,
    }


def sem_html(t):
    return re.sub(r"<[^>]+>", " ", html.unescape(t or ""))


# Fontes de texto clinico com o CID ja escolhido pelo medico: (texto, cid, procedimento, data).
# A anamnese da internacao entra no TREINO do CID (melhora as outras), mas a tela dela nao
# usa a sugestao: acerto medido baixo demais (ver metricas "internacao").
CONSULTAS = {
    "laudo": """select concat_ws(' ', sintomasclinicos, jusinternacao, diagnosticoinicial, provasdiag),
                       cidprincipal, codprocedimento, datasolicitacao
                from laudoautorizacao where datasolicitacao is not null""",
    "alta": """select concat_ws(' ', diaginicial, diagfinal, relato, especificarcomplicacao,
                                procedimentocirespecifi),
                      case when cid2 ~ '^[A-Za-z][0-9]{2}' then cid2 else cid end, null, dataalta
               from resumodealta
               where dataalta is not null and coalesce(status, '') not ilike '%%cancel%%'""",
    "internacao": """select concat_ws(' ', a.frente, a.verso), i.cid, null, i.datainternacao
                     from internacao i join anamneseinternacao a on a.nbam::text = i.bam
                     where i.datainternacao is not null""",
}


def main():
    conn = conectar()
    cur = conn.cursor()
    fontes = {}
    for origem, sql in CONSULTAS.items():
        cur.execute(sql)
        fontes[origem] = cur.fetchall()
    linhas = [r for rs in fontes.values() for r in rs]
    cur.execute("select cd_cod, cd_descr from s_cid")
    desc_cid = {r[0].strip().upper(): re.sub(r"^\S+\s+", "", (r[1] or "").strip()) for r in cur.fetchall()}
    cur.execute("""select distinct on (pa_id) pa_id || pa_dv, pa_dc from s_proced
                   order by pa_id, pa_cmp desc""")
    desc_proc = {r[0]: (r[1] or "").strip() for r in cur.fetchall()}
    NOMES.update(carregar_nomes(cur, list(desc_cid.values()) + list(desc_proc.values())))
    conn.close()
    print("partes de nome removidas do vocabulario:", len(NOMES), flush=True)

    def base(origens, rotulo):
        r = {}
        for o in origens:
            b = [(remover_nomes(normalizar(sem_html(x[0]))), rotulo(x), x[3]) for x in fontes[o]]
            r[o] = [v for v in b if v[1] and len(v[0].split()) >= 3]
        return r

    modelos = {}
    planos = (
        ("cid", base(CONSULTAS.keys(), lambda x: cid_limpo(x[1])), desc_cid),
        ("procedimento", base(["laudo"], lambda x: proc_limpo(x[2])), desc_proc),
    )
    for nome, por_origem, descricoes in planos:
        metricas = avaliar(por_origem)
        todos = so_frequentes([b for bs in por_origem.values() for b in bs])
        vec, clf = treinar([b[0] for b in todos], [b[1] for b in todos])  # modelo final: todo o historico
        m = exportar(vec, clf, descricoes)
        m["metricas"] = metricas
        m["registros_treino"] = {o: len(bs) for o, bs in por_origem.items()}
        modelos[nome] = m
        print(f"{nome}: {len(todos)} registros, {len(m['classes'])} codigos, {len(m['termos'])} termos", flush=True)
        for o, v in metricas.items():
            print(f"   {o:11s} {v}", flush=True)

    saida = {
        "versao": 2,
        "gerado_em": datetime.datetime.now().isoformat(timespec="seconds"),
        "banco": os.environ["PGDATABASE"],
        "periodo": [str(min(r[3] for r in linhas)), str(max(r[3] for r in linhas))],
        "normalizacao": "nfkd-sem-acento|minusculo|sem-digitos|a-z|tokens>=2|unigrama+bigrama|tf-sublinear|l2",
        "modelos": modelos,
    }
    caminho = os.path.abspath(SAIDA)
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with gzip.open(caminho, "wt", encoding="utf-8") as f:
        json.dump(saida, f, ensure_ascii=False, separators=(",", ":"))
    print("gravado:", caminho, os.path.getsize(caminho) // 1024, "KB")


if __name__ == "__main__":
    sys.exit(main())
