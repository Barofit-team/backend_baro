{{- define "workout.name" -}}
{{- .Values.nameOverride | default .Chart.Name -}}
{{- end -}}

{{- define "workout.labels" -}}
app.kubernetes.io/name: {{ include "workout.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: barofit
{{- end -}}

{{- define "workout.selectorLabels" -}}
app.kubernetes.io/name: {{ include "workout.name" . }}
{{- end -}}
