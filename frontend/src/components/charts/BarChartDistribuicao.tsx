import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Rectangle,
  type BarShapeProps,
} from "recharts";

interface DistribuicaoItem {
  categoria: string;
  quantidade: number;
}

interface BarChartDistribuicaoProps {
  data: DistribuicaoItem[];
  // Categoria usada como filtro; as demais barras ficam esmaecidas.
  selecionada?: string | null;
  onSelecionar?: (item: DistribuicaoItem) => void;
}

export function BarChartDistribuicao({ data, selecionada = null, onSelecionar }: BarChartDistribuicaoProps) {
  return (
    <ResponsiveContainer width="100%" height={300}>
      <BarChart
        data={data}
        style={onSelecionar ? { cursor: "pointer" } : undefined}
        // O clique vale para toda a faixa da categoria, não só a barra: barras
        // baixas seriam difíceis de acertar.
        onClick={(estado) => {
          const item = data.find((d) => d.categoria === estado.activeLabel);
          if (item) onSelecionar?.(item);
        }}
      >
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
        <Bar
          dataKey="quantidade"
          fill="var(--terracotta)"
          radius={[4, 4, 0, 0]}
          shape={(props: BarShapeProps) => (
            <Rectangle
              {...props}
              fillOpacity={selecionada && props.payload?.categoria !== selecionada ? 0.3 : 1}
            />
          )}
        />
      </BarChart>
    </ResponsiveContainer>
  );
}
