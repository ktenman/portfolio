import { Page } from '../models/page'

export interface ICrudService<T> {
  getAll(): Promise<T[]>
  create(item: Partial<T>): Promise<T>
  update(id: string | number, item: Partial<T>): Promise<T>
  delete(id: string | number): Promise<void>
}

export interface IPageableService<T> extends ICrudService<T> {
  getPage(pageNumber: number, size: number): Promise<Page<T>>
}
