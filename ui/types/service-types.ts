export interface CrudService<T> {
  getAll: () => Promise<T[]>
  create: (data: Partial<T>) => Promise<T>
  update: (id: number | string, data: Partial<T>) => Promise<T>
  delete?: (id: number | string) => Promise<void>
}

export interface CrudServiceWithDelete<T> extends Omit<CrudService<T>, 'delete'> {
  delete: (id: number | string) => Promise<void>
}
