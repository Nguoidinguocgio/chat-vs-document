// API client — cùng shared/realtime là nơi duy nhất được gọi mạng (luật R-3).
export { request, getHealth, reportApiError, setGlobalErrorHandler, setNewVersionHandler, type Method, type RequestOptions } from './client'
export { ApiError, isApiError } from './errors'
export { openStream } from './stream'
export type { components, paths } from './schema'

import type { components } from './schema'
export type Schemas = components['schemas']
export type CurrentUser = Schemas['CurrentUser']
export type Document = Schemas['Document']
export type DocumentList = Schemas['DocumentList']
export type Conversation = Schemas['Conversation']
export type ConversationDetail = Schemas['ConversationDetail']
export type Message = Schemas['Message']
export type Citation = Schemas['Citation']
