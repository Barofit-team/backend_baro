{{- define "report.name" -}}
{{- .Values.nameOverride | default .Chart.Name -}}
{{- end -}}

{{- define "report.labels" -}}
app.kubernetes.io/name: {{ include "report.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: barofit
{{- end -}}

{{- define "report.selectorLabels" -}}
app.kubernetes.io/name: {{ include "report.name" . }}
{{- end -}}
