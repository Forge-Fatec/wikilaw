import { useEffect, useState } from "react";
import { calcularIndicadores } from './indicadores'

interface DistribuicaoItem {
  categoria: string;
  quantidade: number;
}

export function useDistribuicaoMerito() {
  const [data, setData] = useState<DistribuicaoItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch("/api/dashboard/distribuicao-merito")
      .then((res) => {
        if (!res.ok) throw new Error("Erro na requisição");
        return res.json();
      })
      .then(setData)
      .catch(() => setError("Erro ao carregar distribuição do mérito"))
      .finally(() => setLoading(false));
  }, []);

  return { data, loading, error };
}
