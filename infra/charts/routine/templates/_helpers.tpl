{{- define "routine.name" -}}
{{- .Values.nameOverride | default .Chart.Name -}}
{{- end -}}

{{- define "routine.labels" -}}
app.kubernetes.io/name: {{ include "routine.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: barofit
{{- end -}}

{{- define "routine.selectorLabels" -}}
app.kubernetes.io/name: {{ include "routine.name" . }}
{{- end -}}
