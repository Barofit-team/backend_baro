{{- define "analytics.name" -}}
{{- .Values.nameOverride | default .Chart.Name -}}
{{- end -}}

{{- define "analytics.labels" -}}
app.kubernetes.io/name: {{ include "analytics.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: barofit
{{- end -}}

{{- define "analytics.selectorLabels" -}}
app.kubernetes.io/name: {{ include "analytics.name" . }}
{{- end -}}
