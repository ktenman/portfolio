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

export interface ISearchableService<T> extends ICrudService<T> {
  search(query: string): Promise<T[]>
}

// Combined interface for services that support all operations
export interface IFullService<T> extends IPageableService<T>, ISearchableService<T> {}
