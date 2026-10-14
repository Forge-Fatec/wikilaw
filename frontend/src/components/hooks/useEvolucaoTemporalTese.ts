import { useEffect, useState } from "react";

interface DistribuicaoItem {
  categoria: string;
  quantidade: number;
}

export function useEvolucaoTemporalTese() {
  const [data, setData] = useState<DistribuicaoItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch("/api/dashboard/evolucao-temporal-tese")
      .then((res) => {
        if (!res.ok) throw new Error("Erro na requisição");
        return res.json();
      })
      .then(setData)
      .catch(() => setError("Erro ao carregar evolução temporal da tese"))
      .finally(() => setLoading(false));
  }, []);

  return { data, loading, error };
}