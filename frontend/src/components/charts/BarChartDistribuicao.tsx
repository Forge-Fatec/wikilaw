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
  // Barras deitadas, com a categoria no eixo Y: nomes longos cabem melhor.
  horizontal?: boolean;
  // Categoria usada como filtro; as demais barras ficam esmaecidas.
  selecionada?: string | null;
  onSelecionar?: (item: DistribuicaoItem) => void;
}

const TICK = { fill: "var(--muted)", fontSize: 11 };
const LIMITE_ROTULO = 20;

// O nome completo continua no tooltip.
function abreviar(rotulo: string): string {
  return rotulo.length > LIMITE_ROTULO ? `${rotulo.slice(0, LIMITE_ROTULO - 1)}…` : rotulo;
}

// O tick padrão quebra nomes longos em várias linhas; aqui fica uma linha só,
// abreviada, com o nome inteiro ao passar o mouse.
function TickCategoria({ x, y, payload }: { x?: number; y?: number; payload?: { value: string } }) {
  const rotulo = payload?.value ?? "";
  return (
    <text x={x} y={y} dy={4} textAnchor="end" fill={TICK.fill} fontSize={TICK.fontSize}>
      <title>{rotulo}</title>
      {abreviar(rotulo)}
    </text>
  );
}

export function BarChartDistribuicao({
  data,
  horizontal = false,
  selecionada = null,
  onSelecionar,
}: BarChartDistribuicaoProps) {
  return (
    <ResponsiveContainer width="100%" height={horizontal ? Math.max(180, data.length * 30 + 40) : 230}>
      <BarChart
        data={data}
        layout={horizontal ? "vertical" : "horizontal"}
        margin={{ top: 4, right: 12, bottom: 0, left: 0 }}
        style={onSelecionar ? { cursor: "pointer" } : undefined}
        // O clique vale para toda a faixa da categoria, não só a barra: barras
        // baixas seriam difíceis de acertar.
        onClick={(estado) => {
          const item = data.find((d) => d.categoria === estado.activeLabel);
          if (item) onSelecionar?.(item);
        }}
      >
        <CartesianGrid strokeDasharray="3 3" stroke="var(--line)" />
        {horizontal ? (
          <>
            <XAxis type="number" allowDecimals={false} tick={TICK} />
            <YAxis
              type="category"
              dataKey="categoria"
              tick={<TickCategoria />}
              interval={0}
              width={130}
            />
          </>
        ) : (
          <>
            <XAxis
              dataKey="categoria"
              tick={TICK}
              tickFormatter={abreviar}
              angle={-20}
              textAnchor="end"
              interval={0}
              height={56}
            />
            <YAxis allowDecimals={false} tick={TICK} width={32} />
          </>
        )}
        <Tooltip
          contentStyle={{ border: "1px solid var(--line)", borderRadius: 8, fontSize: 13 }}
        />
        <Bar
          dataKey="quantidade"
          fill="var(--terracotta)"
          radius={horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0]}
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
