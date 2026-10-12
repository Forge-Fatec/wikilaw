import { useEffect, useState } from "react";

interface DistribuicaoItem {
  categoria: string;
  quantidade: number;
}

export function useDistribuicaoCamaraTurma() {
  const [data, setData] = useState<DistribuicaoItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch("/api/dashboard/distribuicao-camara-turma")
      .then((res) => {
        if (!res.ok) throw new Error("Erro na requisição");
        return res.json();
      })
      .then(setData)
      .catch(() => setError("Erro ao carregar distribuição por câmara/turma"))
      .finally(() => setLoading(false));
  }, []);

  return { data, loading, error };
}