import json
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler
from sentence_transformers import SentenceTransformer

MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"
model = SentenceTransformer(MODEL_NAME)
print(f"[embedding] Modelo '{MODEL_NAME}' carregado.", flush=True)

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/embed":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        try:
            data = json.loads(body)
            texto = data.get("texto", "")
            if not texto:
                self.send_error(400, "Campo 'texto' obrigatorio")
                return
            emb = model.encode(texto, normalize_embeddings=True).tolist()
            resp = json.dumps(emb)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(resp.encode())
        except Exception as e:
            self.send_error(500, str(e))

    def log_message(self, fmt, *args):
        print(f"[embedding] {args[0]} {args[1]} {args[2]}", flush=True)

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8001
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()
