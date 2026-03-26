import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { Button, Code } from '@mantine/core';
import type { ColumnDef } from '@tanstack/react-table';
import { auditEventActorDisplay, type AuditEventDto } from '../../api/schemas';
import { DataTable } from '../DataTable';

function shortResourceId(id: string): string {
  return id.length > 10 ? `${id.slice(0, 8)}…` : id;
}

type Props = {
  items: AuditEventDto[];
  /** When set with includeRecordColumn, links the record cell to the record form */
  entityId?: string;
  includeRecordColumn?: boolean;
  onViewPayload: (e: AuditEventDto) => void;
};

export function AuditEventsTable({
  items,
  entityId,
  includeRecordColumn,
  onViewPayload,
}: Props) {
  const columns = useMemo<ColumnDef<AuditEventDto>[]>(() => {
    const cols: ColumnDef<AuditEventDto>[] = [
      {
        id: 'when',
        header: 'When',
        cell: ({ row }) => new Date(row.original.createdAt).toLocaleString(),
      },
      {
        id: 'action',
        header: 'Action',
        cell: ({ row }) => <Code fz="sm">{row.original.action}</Code>,
      },
      {
        id: 'operation',
        header: 'Operation',
        cell: ({ row }) => row.original.operation ?? '—',
      },
    ];

    if (includeRecordColumn && entityId) {
      cols.push({
        id: 'record',
        header: 'Record',
        cell: ({ row }) => {
          const e = row.original;
          return e.resourceId ? (
            <Link to={`/entities/${entityId}/records/${e.resourceId}`}>
              <Code fz="sm">{shortResourceId(e.resourceId)}</Code>
            </Link>
          ) : (
            '—'
          );
        },
      });
    }

    cols.push(
      {
        id: 'actor',
        header: 'Actor',
        cell: ({ row }) => {
          const e = row.original;
          const display = auditEventActorDisplay(e);
          const idOnly = display === (e.actorId ?? '—');
          return idOnly ? (
            <Code fz="sm">{display}</Code>
          ) : (
            <span title={e.actorId ? `User id: ${e.actorId}` : undefined}>{display}</span>
          );
        },
      },
      {
        id: 'payload',
        header: 'Payload',
        meta: { tdStyle: { maxWidth: 160 } },
        cell: ({ row }) => (
          <Button size="xs" variant="default" onClick={() => onViewPayload(row.original)}>
            View payload
          </Button>
        ),
      }
    );

    return cols;
  }, [entityId, includeRecordColumn, onViewPayload]);

  return <DataTable data={items} columns={columns} getRowId={(r) => r.id} minWidth={560} />;
}
