export interface ICrudService<T> {
  getAll(): Promise<T[]>
  create(item: Partial<T>): Promise<T>
  update(id: string | number, item: Partial<T>): Promise<T>
  delete(id: string | number): Promise<void>
}
