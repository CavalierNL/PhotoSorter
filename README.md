# PhotoSorter
Tool to sort foto's into specifically named folders, eg. `<year>/<year-month-day>[ - <camera/author] -/<filename>`. This makes it easy to add a desciption later and perhaps join folders together manually based on days with the same event (holidays and such).

The dates are determined by the following information (in this order):
* A date in the name of the file (date or timestamp)
* The date gathered from metadata in the file
* The creation date
* The last modified date
* The current date
