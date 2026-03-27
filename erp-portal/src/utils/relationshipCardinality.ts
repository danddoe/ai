/** Human-readable inverse for the entity on the other end of the same edge (single stored relationship row). */
export function inverseCardinalityLabel(cardinality: string): string | null {
  const c = (cardinality || '').trim().toLowerCase();
  switch (c) {
    case 'one-to-many':
      return 'many-to-one';
    case 'many-to-one':
      return 'one-to-many';
    case 'one-to-one':
      return 'one-to-one';
    case 'many-to-many':
      return 'many-to-many';
    default:
      return null;
  }
}

/**
 * When the user expresses cardinality from the <strong>child</strong> entity with {@code many-to-one},
 * the stored row uses parent-as-from with {@code one-to-many} (record links / DDL convention).
 */
export function storageCardinalityAndDirection(
  thisEntityId: string,
  targetEntityId: string,
  formCardinality: string
): { fromEntityId: string; toEntityId: string; cardinality: string } {
  const c = (formCardinality || '').trim().toLowerCase();
  if (c === 'many-to-one') {
    return {
      fromEntityId: targetEntityId,
      toEntityId: thisEntityId,
      cardinality: 'one-to-many',
    };
  }
  return {
    fromEntityId: thisEntityId,
    toEntityId: targetEntityId,
    cardinality: formCardinality,
  };
}
