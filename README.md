# Cradle Cache

# Overview
This component serves as a backend for rpt-viewer. It will connect to arangoDB and expose the data stored in there as REST resources.

# API

### REST

Every route accepts boolean `probe` parameter, if set to `true` then exception will not be thrown in case data result is empty, else `DataNotFoundException` will be thrown. defaults to `false`.

`http://localhost:8080/event/{book}/{scope}/{id}` - returns a single event with the specified book, scope and id.

`http://localhost:8080/message/{id}` - returns a single message with specified id.

`http://localhost:8080/messageBody/{id}` - returns body of a single message with the specified id.

`http://localhost:8080/messageStream` - returns a list of message stream names.

`http://localhost:8080/events/{book}` - return a list of events that matches the filter.
- `book` - text, book of the event.
- `limit` - number, maximum returned events. If not specified, every event that matches will be returned.
- filters:
  - `name` - will match the events which name contains one of the given substrings. Parameters: `values` - text, accepts multiple values, case-insensitive, `negative`    - boolean, `conjunct` - boolean.  
  - `type` - will match the events which type contains one of the given substrings. Parameters: `values` - text, accepts multiple values, case-insensitive, `negative`    - boolean, `conjunct` - boolean.  
  - `parentId` - string. 
  - `body` - string.
  - `status` - boolean.

`http://localhost:8080/eventChildren` - returns children of an event with specified id. Accepts following query parameters (additoinally, supports Filters API below):
- `id` - text, id of the event. If not specified, events without parent and satisfying remaining filters will be returned.
- `offset` - number, first `offset` number of elements satisfying filters will be ignored.
- `limit` - number, maximum returned events. If not specified, every event that matches will be returned.
- `search-depth` - number, maximum depth of search starting from the root node. defaults to `1`.
- filters:
  - `name` - will match the events which name contains one of the given substrings. Parameters: `values` - text, accepts multiple values, case-insensitive, `negative`    - boolean, `conjunct` - boolean.  
  - `type` - will match the events which type contains one of the given substrings. Parameters: `values` - text, accepts multiple values, case-insensitive, `negative`    - boolean, `conjunct` - boolean.  

### SSE

#### Filters API

Filters are formed as follows:
- `filters={filter name}` - you must register the filter by specifying its name.  
- `{filter name}-{parameter}={parameter value}` - each filter parameter

****Types of filter `parameter`****:
- `value / values` - text, number, boolean, etc. - one / many values to match against a filter.
- `negative` - boolean. - Negates the result of the filter. To retrieve data that does NOT match the specified filter. Defaults to `false`.
- `conjunct` - boolean. - Used if the filter takes several values. If `true` then the element must match ALL given `values`. If `false` then element must match ANY of `values`. *Not used in filters with one possible value*. Defaults to `false`.

##### SSE requests API

`http://localhost:8080/search/sse/messages` - create a sse channel of messages that matches the filter. Accepts following query parameters:
- `page-size` - number, size of the page. 
- `page-number` - number, count of pages.
- `search-direction` - `next`/`previous` - Sets the lookup direction. Can be used for pagination. Defaults to `next`.
- filters:
  - `start-timestamp` - number, unix timestamp in milliseconds - Sets the search starting point. 
  - `stream` - text, accepts multiple values - Sets the stream ids to search in. Case-sensitive. **Required**. 
  - `end-timestamp` - number, unix timestamp in milliseconds - Sets the timestamp to which the search will be performed, starting with `start-timestamp`. When `search-direction` is `previous`, `end-timestamp` must be less then `start-timestamp`. Defaults to `null` (the search is carried out endlessly into the past or the future).

