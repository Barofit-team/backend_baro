{{- define "identity.name" -}}
{{- .Values.nameOverride | default .Chart.Name -}}
{{- end -}}

{{- define "identity.labels" -}}
app.kubernetes.io/name: {{ include "identity.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: barofit
{{- end -}}

{{- define "identity.selectorLabels" -}}
app.kubernetes.io/name: {{ include "identity.name" . }}
{{- end -}}
