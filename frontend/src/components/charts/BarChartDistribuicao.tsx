import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";

interface DistribuicaoItem {
  categoria: string;
  quantidade: number;
}

interface BarChartDistribuicaoProps {
  data: DistribuicaoItem[];
  titulo: string;
}

export function BarChartDistribuicao({ data, titulo }: BarChartDistribuicaoProps) {
  return (
    <div className="chart-container">
      <h3>{titulo}</h3>
      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--line)" />
          <XAxis
            dataKey="categoria"
            tick={{ fill: "var(--muted)", fontSize: 12 }}
            angle={-20}
            textAnchor="end"
            interval={0}
            height={60}
          />
          <YAxis allowDecimals={false} tick={{ fill: "var(--muted)", fontSize: 12 }} />
          <Tooltip
            contentStyle={{ border: "1px solid var(--line)", borderRadius: 8, fontSize: 13 }}
          />
          <Bar dataKey="quantidade" fill="var(--terracotta)" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}