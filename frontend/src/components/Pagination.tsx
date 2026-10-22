interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onChange: (page: number) => void;
}

export function Pagination({ page, totalPages, totalElements, size, onChange }: PaginationProps) {
  const first = totalElements === 0 ? 0 : page * size + 1;
  const last = Math.min(totalElements, (page + 1) * size);
  return (
    <nav className="pagination" aria-label="Pagination" data-testid="pagination">
      <span>
        {first}–{last} of {totalElements}
      </span>
      <span className="row">
        <button
          type="button"
          className="btn small"
          onClick={() => onChange(page - 1)}
          disabled={page <= 0}
        >
          Previous
        </button>
        <span>
          Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
        </span>
        <button
          type="button"
          className="btn small"
          onClick={() => onChange(page + 1)}
          disabled={page + 1 >= totalPages}
        >
          Next
        </button>
      </span>
    </nav>
  );
}
