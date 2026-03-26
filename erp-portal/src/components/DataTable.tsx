import type { CSSProperties } from 'react';
import { flexRender, getCoreRowModel, useReactTable, type ColumnDef } from '@tanstack/react-table';
import { Table } from '@mantine/core';

export type DataTableColMeta = {
  thStyle?: CSSProperties;
  tdStyle?: CSSProperties;
};

export type DataTableProps<T> = {
  data: T[];
  columns: ColumnDef<T, unknown>[];
  getRowId?: (originalRow: T, index: number, parent?: unknown) => string;
  minWidth?: number;
};

export function DataTable<T>({ data, columns, getRowId, minWidth = 400 }: DataTableProps<T>) {
  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getRowId,
  });

  return (
    <Table.ScrollContainer minWidth={minWidth} type="scroll">
      <Table striped highlightOnHover withTableBorder withColumnBorders>
        <Table.Thead>
          {table.getHeaderGroups().map((hg) => (
            <Table.Tr key={hg.id}>
              {hg.headers.map((header) => {
                const m = header.column.columnDef.meta as DataTableColMeta | undefined;
                return (
                  <Table.Th key={header.id} style={m?.thStyle}>
                    {header.isPlaceholder
                      ? null
                      : flexRender(header.column.columnDef.header, header.getContext())}
                  </Table.Th>
                );
              })}
            </Table.Tr>
          ))}
        </Table.Thead>
        <Table.Tbody>
          {table.getRowModel().rows.map((row) => (
            <Table.Tr key={row.id}>
              {row.getVisibleCells().map((cell) => {
                const m = cell.column.columnDef.meta as DataTableColMeta | undefined;
                return (
                  <Table.Td key={cell.id} style={m?.tdStyle}>
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </Table.Td>
                );
              })}
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Table.ScrollContainer>
  );
}
